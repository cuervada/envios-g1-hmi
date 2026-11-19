package edu.uade.enviosg1.data

import edu.uade.enviosg1.model.DestinoEntrega
import edu.uade.enviosg1.model.EntregaRegistrada
import edu.uade.enviosg1.model.EntregaRobot
import edu.uade.enviosg1.model.EstadoCompartimento
import edu.uade.enviosg1.model.EstadoOperativo
import edu.uade.enviosg1.model.FaseEntrega
import edu.uade.enviosg1.model.MisionRobot
import edu.uade.enviosg1.model.ResultadoEntrega
import edu.uade.enviosg1.model.TelemetriaRobot
import edu.uade.enviosg1.model.UBICACION_RECEPCION

private const val TRAYECTO_MS = 10_000L
internal const val ESPERA_RETIRO_SEGUNDOS = 4 * 60 + 30
private const val PLAZO_RETIRO_MS = ESPERA_RETIRO_SEGUNDOS * 1_000L
private const val VELOCIDAD_MARCHA = 0.6

internal data class PosicionGuardada(
    val fase: FaseEntrega,
    val recorridoMs: Long,
    val ubicacion: String,
    val progreso: Int
)

internal data class EntregaSimulada(
    val datos: EntregaRobot,
    val inicioFaseMs: Long,
    val compartimento: EstadoCompartimento = EstadoCompartimento.CerradoTrabado,
    val conPaquete: Boolean = false,
    val finRegresoEpochMs: Long? = null,
    private val posicionGuardada: PosicionGuardada? = null,
    private val origenRegresoPorcentaje: Int = 100
) {
    val fase: FaseEntrega
        get() = datos.fase

    private val duracionTrayectoMs: Long
        get() = if (datos.esRegreso) calcularDuracionRegreso(origenRegresoPorcentaje) else TRAYECTO_MS

    fun cambiarFase(nuevaFase: FaseEntrega, ahoraMs: Long) =
        copy(datos = datos.copy(fase = nuevaFase), inicioFaseMs = ahoraMs)

    fun avanzar(ahoraMs: Long, horaMs: Long): EntregaSimulada {
        val finTrayectoMs = inicioFaseMs + duracionTrayectoMs
        val actual = when {
            ahoraMs < finTrayectoMs -> this
            fase == FaseEntrega.EnCamino ->
                cambiarFase(FaseEntrega.EsperandoRetiro, finTrayectoMs)

            fase == FaseEntrega.Regresando -> {
                val siguiente = if (conPaquete) FaseEntrega.DescargaEnRecepcion else FaseEntrega.Finalizada
                cambiarFase(siguiente, finTrayectoMs)
            }
            else -> this
        }

        val finEsperaMs = actual.inicioFaseMs + PLAZO_RETIRO_MS
        if (!actual.esperandoPaquete || !actual.conPaquete || ahoraMs < finEsperaMs) return actual

        val vencimientoEpochMs = horaMs - (ahoraMs - finEsperaMs)
        return actual.copy(
            datos = actual.datos.copy(fase = FaseEntrega.RetiroVencido, retiroVencidoMs = vencimientoEpochMs),
            inicioFaseMs = finEsperaMs
        )
    }

    fun simularVencimiento(ahoraMs: Long, horaMs: Long): EntregaSimulada =
        if (esperandoPaquete && conPaquete) {
            copy(inicioFaseMs = ahoraMs - PLAZO_RETIRO_MS).avanzar(ahoraMs, horaMs)
        } else {
            this
        }

    private val esperandoPaquete: Boolean
        get() = fase == FaseEntrega.EsperandoRetiro || fase == FaseEntrega.RetirandoPaquete

    fun inmovilizar(nuevaFase: FaseEntrega, lectura: TelemetriaRobot, ahoraMs: Long): EntregaSimulada {
        val posicion = posicionGuardada ?: PosicionGuardada(
            fase = fase,
            recorridoMs = (ahoraMs - inicioFaseMs).coerceIn(0, duracionTrayectoMs),
            ubicacion = lectura.ubicacion,
            progreso = lectura.mision?.progreso ?: 0
        )
        return cambiarFase(nuevaFase, ahoraMs).copy(posicionGuardada = posicion)
    }

    fun reanudar(ahoraMs: Long, horaMs: Long): EntregaSimulada {
        val guardada = checkNotNull(posicionGuardada)
        val finRegreso = if (guardada.fase == FaseEntrega.Regresando) {
            horaMs + (duracionTrayectoMs - guardada.recorridoMs)
        } else {
            null
        }
        return cambiarFase(guardada.fase, ahoraMs - guardada.recorridoMs)
            .copy(posicionGuardada = null, finRegresoEpochMs = finRegreso)
    }

    fun retirarPaquete(horaMs: Long) = copy(
        datos = datos.copy(fase = FaseEntrega.RetiroConfirmado, retiroConfirmadoMs = horaMs),
        conPaquete = false
    )

    fun iniciarRegreso(lectura: TelemetriaRobot, ahoraMs: Long, horaMs: Long): EntregaSimulada {
        val progreso = lectura.mision?.progreso ?: 100
        val origen = if (datos.esRegreso) {
            origenRegresoPorcentaje * (100 - progreso) / 100
        } else {
            progreso
        }
        return copy(
            datos = datos.copy(fase = FaseEntrega.Regresando, esRegreso = true),
            inicioFaseMs = ahoraMs,
            finRegresoEpochMs = horaMs + calcularDuracionRegreso(origen),
            posicionGuardada = null,
            origenRegresoPorcentaje = origen
        )
    }

    fun registro(): EntregaRegistrada? {
        val destino = datos.destino ?: return null
        val vencimiento = datos.retiroVencidoMs
        if (vencimiento != null) {
            val regreso = finRegresoEpochMs.takeIf {
                fase == FaseEntrega.DescargaEnRecepcion || fase == FaseEntrega.Finalizada
            }
            return EntregaRegistrada(datos.codigo, destino, ResultadoEntrega.NoRetirado, vencimiento, regreso)
        }

        val retiro = datos.retiroConfirmadoMs
        if (fase != FaseEntrega.Finalizada || retiro == null) return null
        return EntregaRegistrada(datos.codigo, destino, ResultadoEntrega.Entregada, retiro, finRegresoEpochMs)
    }

    fun aplicarALectura(base: TelemetriaRobot, ahoraMs: Long): TelemetriaRobot {
        if (fase == FaseEntrega.Finalizada) return base.copy(entrega = datos)
        val destino = datos.destino
        val estado = when (fase) {
            FaseEntrega.Preparando -> EstadoOperativo.Preparando
            FaseEntrega.EnCamino -> EstadoOperativo.EnCamino
            FaseEntrega.Regresando -> EstadoOperativo.RegresandoABase
            FaseEntrega.Pausada -> EstadoOperativo.Pausado
            FaseEntrega.DescargaEnRecepcion, FaseEntrega.RetiroVencido -> EstadoOperativo.Detenido
            FaseEntrega.Cancelada, FaseEntrega.Detenida -> EstadoOperativo.Detenido
            else -> EstadoOperativo.EsperandoRetiro
        }
        val enMarcha = estado == EstadoOperativo.EnCamino || estado == EstadoOperativo.RegresandoABase
        val progreso = when {
            posicionGuardada != null -> posicionGuardada.progreso
            enMarcha -> (((ahoraMs - inicioFaseMs) * 100) / duracionTrayectoMs).coerceIn(0, 99).toInt()
            fase == FaseEntrega.DescargaEnRecepcion || fase == FaseEntrega.RetiroVencido -> 100
            estado == EstadoOperativo.EsperandoRetiro -> 100
            else -> 0
        }
        val (etapa, ubicacion) = when {
            posicionGuardada != null -> descripcionInmovil() to posicionGuardada.ubicacion
            destino == null -> "Elegí un destino" to base.ubicacion
            fase == FaseEntrega.Preparando -> "Preparando entrega" to base.ubicacion
            fase == FaseEntrega.EnCamino -> tramoDeIda(destino, progreso)
            fase == FaseEntrega.Regresando -> tramoDeRegreso(destino, progreso, origenRegresoPorcentaje)
            fase == FaseEntrega.DescargaEnRecepcion -> "Descarga en Recepción" to UBICACION_RECEPCION
            fase == FaseEntrega.RetiroVencido -> "Paquete no retirado" to "Piso 2 · ${destino.nombre}"
            else -> "En la puerta de la oficina" to "Piso 2 · ${destino.nombre}"
        }
        val espera = when {
            fase == FaseEntrega.RetiroVencido -> 0
            esperandoPaquete ->
                ((inicioFaseMs + PLAZO_RETIRO_MS - ahoraMs + 999) / 1_000).coerceAtLeast(0).toInt()
            else -> null
        }
        return base.copy(
            estado = estado,
            mision = destino?.let { MisionRobot(it.nombre, etapa, progreso) },
            ubicacion = ubicacion,
            velocidad = if (enMarcha) VELOCIDAD_MARCHA else 0.0,
            compartimento = compartimento,
            conPaquete = conPaquete,
            esperaRestanteSegundos = espera,
            entrega = datos
        )
    }

    private fun descripcionInmovil() = when (fase) {
        FaseEntrega.Pausada -> "Misión en pausa"
        FaseEntrega.Cancelada -> "Misión cancelada"
        else -> "Detención operativa"
    }
}

internal fun tramoDeIda(destino: DestinoEntrega, progreso: Int): Pair<String, String> = when {
    progreso < 36 -> "Saliendo de Recepción" to "Planta baja · Pasillo de Recepción"
    progreso < 63 -> "En ascensor" to "Ascensor · Hacia el piso 2"
    else -> "Atravesando Pasillo B" to destino.sector
}

private fun calcularDuracionRegreso(origen: Int): Long =
    (TRAYECTO_MS * origen / 100).coerceAtLeast(1_000L)

private fun tramoDeRegreso(
    destino: DestinoEntrega,
    progreso: Int,
    origen: Int
): Pair<String, String> {
    val posicionEnLaIda = origen * (100 - progreso) / 100
    return when {
        posicionEnLaIda >= 63 -> "Volviendo por Pasillo B" to destino.sector
        posicionEnLaIda >= 36 -> "En ascensor" to "Ascensor · Bajando"
        else -> "Volviendo a Recepción" to "Planta baja · Pasillo de Recepción"
    }
}

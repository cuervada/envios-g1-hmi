package edu.uade.enviosg1.data

import edu.uade.enviosg1.model.DestinoEntrega
import edu.uade.enviosg1.model.EntregaRegistrada
import edu.uade.enviosg1.model.EntregaRobot
import edu.uade.enviosg1.model.EstadoCompartimento
import edu.uade.enviosg1.model.EstadoOperativo
import edu.uade.enviosg1.model.FaseEntrega
import edu.uade.enviosg1.model.MisionRobot
import edu.uade.enviosg1.model.TelemetriaRobot

private const val CONFIRMACION_MS = 1_000L
private const val TRAYECTO_MS = 10_000L
private const val ESPERA_RETIRO_SEGUNDOS = 4 * 60 + 30
private const val VELOCIDAD_MARCHA = 0.6

internal data class EntregaSimulada(
    val datos: EntregaRobot,
    val inicioFaseMs: Long,
    val compartimento: EstadoCompartimento = EstadoCompartimento.CerradoTrabado,
    val conPaquete: Boolean = false,
    val finRegresoEpochMs: Long? = null
) {
    val fase: FaseEntrega
        get() = datos.fase

    fun cambiarFase(nuevaFase: FaseEntrega, ahoraMs: Long) =
        copy(datos = datos.copy(fase = nuevaFase), inicioFaseMs = ahoraMs)

    fun avanzar(ahoraMs: Long): EntregaSimulada {
        val salidaMs = inicioFaseMs + CONFIRMACION_MS
        val entrega = if (fase == FaseEntrega.InicioPendiente && ahoraMs >= salidaMs) {
            cambiarFase(FaseEntrega.EnCamino, salidaMs)
        } else {
            this
        }
        val finTrayectoMs = entrega.inicioFaseMs + TRAYECTO_MS
        if (ahoraMs < finTrayectoMs) return entrega
        return when (entrega.fase) {
            FaseEntrega.EnCamino -> entrega.cambiarFase(FaseEntrega.EsperandoRetiro, finTrayectoMs)
            FaseEntrega.Regresando -> entrega.cambiarFase(FaseEntrega.Finalizada, finTrayectoMs)
            else -> entrega
        }
    }

    fun retirarPaquete(horaMs: Long) = copy(
        datos = datos.copy(fase = FaseEntrega.RetiroConfirmado, retiroConfirmadoMs = horaMs),
        conPaquete = false
    )

    fun iniciarRegreso(ahoraMs: Long, horaMs: Long) =
        cambiarFase(FaseEntrega.Regresando, ahoraMs).copy(finRegresoEpochMs = horaMs + TRAYECTO_MS)

    fun registro() = EntregaRegistrada(
        codigo = datos.codigo,
        destino = checkNotNull(datos.destino),
        retiroConfirmadoMs = checkNotNull(datos.retiroConfirmadoMs),
        regresoMs = checkNotNull(finRegresoEpochMs)
    )

    fun aplicarALectura(base: TelemetriaRobot, ahoraMs: Long): TelemetriaRobot {
        if (fase == FaseEntrega.Finalizada) return base.copy(entrega = datos)

        val destino = datos.destino
        val estado = when (fase) {
            FaseEntrega.Preparando, FaseEntrega.InicioPendiente -> EstadoOperativo.Preparando
            FaseEntrega.EnCamino -> EstadoOperativo.EnCamino
            FaseEntrega.Regresando -> EstadoOperativo.RegresandoABase
            else -> EstadoOperativo.EsperandoRetiro
        }
        val enMarcha = estado == EstadoOperativo.EnCamino || estado == EstadoOperativo.RegresandoABase
        val progreso = when {
            enMarcha -> (((ahoraMs - inicioFaseMs) * 100) / TRAYECTO_MS).coerceIn(0, 99).toInt()
            estado == EstadoOperativo.EsperandoRetiro -> 100
            else -> 0
        }
        val (etapa, ubicacion) = when {
            destino == null -> "Elegí un destino" to base.ubicacion
            fase == FaseEntrega.Preparando -> "Preparando entrega" to base.ubicacion
            fase == FaseEntrega.InicioPendiente -> "Esperando confirmación" to base.ubicacion
            fase == FaseEntrega.EnCamino -> tramoDeIda(destino, progreso)
            fase == FaseEntrega.Regresando -> tramoDeRegreso(destino, progreso)
            else -> "En la puerta de la oficina" to "Piso 2 · ${destino.nombre}"
        }
        val espera = if (fase == FaseEntrega.EsperandoRetiro || fase == FaseEntrega.RetirandoPaquete) {
            (ESPERA_RETIRO_SEGUNDOS - (ahoraMs - inicioFaseMs) / 1_000).coerceAtLeast(0).toInt()
        } else {
            null
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
}

internal fun tramoDeIda(destino: DestinoEntrega, progreso: Int): Pair<String, String> = when {
    progreso < 36 -> "Saliendo de Recepción" to "Planta baja · Pasillo de Recepción"
    progreso < 63 -> "En ascensor" to "Ascensor · Hacia el piso 2"
    else -> "Atravesando Pasillo B" to destino.sector
}

private fun tramoDeRegreso(destino: DestinoEntrega, progreso: Int): Pair<String, String> = when {
    progreso < 36 -> "Saliendo de ${destino.nombre}" to destino.sector
    progreso < 63 -> "En ascensor" to "Ascensor · Bajando"
    else -> "Volviendo a Recepción" to "Planta baja · Pasillo de Recepción"
}

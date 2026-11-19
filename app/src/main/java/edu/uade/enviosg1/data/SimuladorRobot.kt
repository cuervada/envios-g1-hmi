package edu.uade.enviosg1.data

import edu.uade.enviosg1.model.AlertaRobot
import edu.uade.enviosg1.model.ComandoRobot
import edu.uade.enviosg1.model.DestinoEntrega
import edu.uade.enviosg1.model.EntregaRegistrada
import edu.uade.enviosg1.model.EntregaRobot
import edu.uade.enviosg1.model.EstadoCompartimento
import edu.uade.enviosg1.model.EstadoOperativo
import edu.uade.enviosg1.model.EstadoOrden
import edu.uade.enviosg1.model.FaseEntrega
import edu.uade.enviosg1.model.MisionRobot
import edu.uade.enviosg1.model.OrdenRobot
import edu.uade.enviosg1.model.RespuestaOrden
import edu.uade.enviosg1.model.TelemetriaRobot
import edu.uade.enviosg1.model.motivoBloqueo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

private const val INTERVALO_MS = 1_000L
private const val RETARDO_RESPUESTA_MS = 1_000L
private const val CARGA_SEGUNDOS = 5
private const val LLEGADA_SEGUNDOS = 15
private const val DURACION_PRUEBA_SEGUNDOS = 30
private const val ESPERA_RETIRO_SEGUNDOS = 4 * 60 + 30
private val destinoPrueba = DestinoEntrega.Oficina204

private data class OrdenPendiente(
    val orden: OrdenRobot,
    val recibidaMs: Long,
    val horaRecepcionMs: Long,
    val rechazoForzado: Boolean
)

private data class DetencionSinEntrega(
    val ubicacion: String,
    val mision: MisionRobot?,
    val compartimento: EstadoCompartimento,
    val conPaquete: Boolean
)

private data class EstadoSimulador(
    val inicioPruebaMs: Long? = null,
    val entrega: EntregaSimulada? = null,
    val ultimaEntrega: EntregaRegistrada? = null,
    val siguienteNumero: Int = 24,
    val pendiente: OrdenPendiente? = null,
    val ultimaRespuesta: RespuestaOrden? = null,
    val rechazoPreparado: Boolean = false,
    val compartimentoReconocido: Boolean = false,
    val detencionSinEntrega: DetencionSinEntrega? = null
) {
    fun avanzarEntrega(ahoraMs: Long): EstadoSimulador {
        val anterior = entrega ?: return this
        val actual = anterior.avanzar(ahoraMs)
        if (actual == anterior) return this
        val registro = if (actual.fase == FaseEntrega.Finalizada) actual.registro() else ultimaEntrega
        return copy(entrega = actual, ultimaEntrega = registro)
    }
}

internal class SimuladorRobot(
    private val relojMonotonicoMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val horaActualMs: () -> Long = System::currentTimeMillis
) {
    private val inicioSimuladorMs = relojMonotonicoMs()
    private val estado = MutableStateFlow(EstadoSimulador())

    private val intervalos = flow {
        while (true) {
            emit(Unit)
            delay(INTERVALO_MS)
        }
    }

    val telemetria: Flow<TelemetriaRobot> = intervalos.combine(estado) { _, operacion ->
        val ahora = relojMonotonicoMs()
        leer(avanzar(operacion, ahora), ahora, horaActualMs())
    }

    fun ejecutar(comando: ComandoRobot) {
        estado.update { anterior ->
            val ahora = relojMonotonicoMs()
            val hora = horaActualMs()
            val actual = avanzar(anterior, ahora)
            val lectura = leer(actual, ahora, hora)
            if (comando is OrdenRobot) {
                recibirOrden(actual, lectura, comando, ahora, hora)
            } else if (actual.pendiente != null) {
                actual
            } else {
                aplicarAccion(actual, lectura, comando, ahora, hora)
            }
        }
    }

    private fun recibirOrden(
        estado: EstadoSimulador,
        lectura: TelemetriaRobot,
        orden: OrdenRobot,
        ahoraMs: Long,
        horaMs: Long
    ): EstadoSimulador {
        val esDetencion = orden == OrdenRobot.Detener
        val pendiente = estado.pendiente
        if (pendiente != null && (!esDetencion || pendiente.orden == OrdenRobot.Detener)) return estado
        val motivo = lectura.motivoBloqueo(orden)
        if (motivo != null) {
            return estado.copy(ultimaRespuesta = RespuestaOrden(orden, EstadoOrden.Rechazada, motivo))
        }
        val forzar = estado.rechazoPreparado && !esDetencion
        return estado.copy(
            pendiente = OrdenPendiente(orden, ahoraMs, horaMs, forzar),
            rechazoPreparado = estado.rechazoPreparado && !forzar
        )
    }

    private fun avanzar(anterior: EstadoSimulador, ahoraMs: Long): EstadoSimulador {
        val pendiente = anterior.pendiente ?: return anterior.avanzarEntrega(ahoraMs)
        val instanteRespuesta = pendiente.recibidaMs + RETARDO_RESPUESTA_MS
        if (ahoraMs < instanteRespuesta) return anterior.avanzarEntrega(ahoraMs)

        val horaRespuesta = pendiente.horaRecepcionMs + RETARDO_RESPUESTA_MS
        val actual = anterior.avanzarEntrega(instanteRespuesta).copy(pendiente = null)
        val lectura = leer(actual, instanteRespuesta, horaRespuesta)
        val motivo = if (pendiente.rechazoForzado) {
            "El simulador rechazó esta orden por la prueba de rechazo."
        } else {
            lectura.motivoBloqueo(pendiente.orden)
        }
        val resuelto = if (motivo != null) {
            actual.copy(ultimaRespuesta = RespuestaOrden(pendiente.orden, EstadoOrden.Rechazada, motivo))
        } else {
            confirmarOrden(actual, lectura, pendiente.orden, instanteRespuesta, horaRespuesta)
        }
        return resuelto.avanzarEntrega(ahoraMs)
    }

    private fun confirmarOrden(
        estado: EstadoSimulador,
        lectura: TelemetriaRobot,
        orden: OrdenRobot,
        ahoraMs: Long,
        horaMs: Long
    ): EstadoSimulador {
        val entrega = estado.entrega
        val actual = when (orden) {
            OrdenRobot.Iniciar -> estado.copy(
                entrega = checkNotNull(entrega).cambiarFase(FaseEntrega.EnCamino, ahoraMs)
            )
            OrdenRobot.Pausar -> estado.copy(
                entrega = checkNotNull(entrega).inmovilizar(FaseEntrega.Pausada, lectura, ahoraMs)
            )
            OrdenRobot.Reanudar -> estado.copy(entrega = checkNotNull(entrega).reanudar(ahoraMs, horaMs))
            OrdenRobot.Cancelar -> estado.copy(
                entrega = checkNotNull(entrega).inmovilizar(FaseEntrega.Cancelada, lectura, ahoraMs)
            )
            OrdenRobot.Regresar -> estado.copy(entrega = checkNotNull(entrega).iniciarRegreso(ahoraMs, horaMs))
            OrdenRobot.Detener -> detener(estado, lectura, ahoraMs)
            OrdenRobot.ReconocerAlertas -> estado.copy(compartimentoReconocido = true)
        }
        val detalle = when (orden) {
            OrdenRobot.Iniciar -> "El G1 inició el recorrido a la oficina."
            OrdenRobot.Pausar -> "El G1 está inmóvil y conserva el progreso del recorrido."
            OrdenRobot.Reanudar -> "El G1 retomó el recorrido desde el punto donde se pausó."
            OrdenRobot.Cancelar -> "La misión se canceló y el G1 quedó inmóvil."
            OrdenRobot.Regresar -> "El G1 inició el regreso hacia Recepción."
            OrdenRobot.Detener -> "El G1 quedó inmóvil. Velocidad: 0,0 m/s."
            OrdenRobot.ReconocerAlertas -> "Se registró que el supervisor vio la alerta."
        }
        return actual.copy(ultimaRespuesta = RespuestaOrden(orden, EstadoOrden.Confirmada, detalle))
    }

    private fun detener(estado: EstadoSimulador, lectura: TelemetriaRobot, ahoraMs: Long): EstadoSimulador {
        val entrega = estado.entrega?.takeUnless { it.fase == FaseEntrega.Finalizada }
        if (entrega != null) {
            return estado.copy(entrega = entrega.inmovilizar(FaseEntrega.Detenida, lectura, ahoraMs))
        }
        return estado.copy(
            inicioPruebaMs = null,
            entrega = null,
            detencionSinEntrega = DetencionSinEntrega(
                lectura.ubicacion,
                lectura.mision,
                lectura.compartimento,
                lectura.conPaquete
            )
        )
    }

    private fun aplicarAccion(
        estado: EstadoSimulador,
        lectura: TelemetriaRobot,
        comando: ComandoRobot,
        ahoraMs: Long,
        horaMs: Long
    ): EstadoSimulador = when (comando) {
        ComandoRobot.ProbarTelemetria -> {
            if (lectura.estado == EstadoOperativo.Disponible) {
                estado.copy(
                    inicioPruebaMs = ahoraMs,
                    entrega = null,
                    compartimentoReconocido = false,
                    ultimaRespuesta = null
                )
            } else {
                estado
            }
        }
        ComandoRobot.NuevaEntrega -> {
            if (estado.entrega?.fase == FaseEntrega.Finalizada) {
                estado.copy(entrega = null, ultimaRespuesta = null)
            } else {
                estado
            }
        }
        is ComandoRobot.ElegirDestino -> elegirDestino(estado, lectura, comando.destino, ahoraMs)
        ComandoRobot.AlternarRechazo -> estado.copy(rechazoPreparado = !estado.rechazoPreparado)
        else -> {
            val entrega = estado.entrega
            if (entrega != null && lectura.conectado) {
                val actual = accionarCompartimento(entrega, comando, ahoraMs, horaMs)
                val nuevaApertura = entrega.compartimento != EstadoCompartimento.Abierto &&
                    actual.compartimento == EstadoCompartimento.Abierto
                estado.copy(
                    entrega = actual,
                    compartimentoReconocido = estado.compartimentoReconocido && !nuevaApertura
                )
            } else {
                estado
            }
        }
    }

    private fun elegirDestino(
        estado: EstadoSimulador,
        lectura: TelemetriaRobot,
        destino: DestinoEntrega,
        ahoraMs: Long
    ): EstadoSimulador {
        if (!lectura.puedeElegirDestino) return estado
        val entrega = estado.entrega
        if (entrega?.fase == FaseEntrega.Preparando) {
            return estado.copy(entrega = entrega.copy(datos = entrega.datos.copy(destino = destino)))
        }
        return estado.copy(
            inicioPruebaMs = null,
            entrega = EntregaSimulada(EntregaRobot(estado.siguienteNumero, destino), ahoraMs),
            siguienteNumero = estado.siguienteNumero + 1,
            compartimentoReconocido = false,
            ultimaRespuesta = null
        )
    }

    private fun accionarCompartimento(
        entrega: EntregaSimulada,
        comando: ComandoRobot,
        ahoraMs: Long,
        horaMs: Long
    ): EntregaSimulada {
        if (entrega.datos.destino == null) return entrega
        val preparando = entrega.fase == FaseEntrega.Preparando
        val abierto = entrega.compartimento == EstadoCompartimento.Abierto
        return when (comando) {
            ComandoRobot.CambiarDestino ->
                if (preparando) entrega.copy(datos = entrega.datos.copy(destino = null)) else entrega

            ComandoRobot.AbrirCompartimento -> when {
                preparando && !abierto -> entrega.copy(compartimento = EstadoCompartimento.Abierto)
                entrega.fase == FaseEntrega.EsperandoRetiro && !abierto && entrega.conPaquete ->
                    entrega.copy(
                        datos = entrega.datos.copy(fase = FaseEntrega.RetirandoPaquete),
                        compartimento = EstadoCompartimento.Abierto
                    )
                else -> entrega
            }

            ComandoRobot.CargarPaquete ->
                if (preparando && abierto) entrega.copy(conPaquete = true) else entrega

            ComandoRobot.CerrarYTrabar -> when {
                preparando && abierto -> entrega.copy(compartimento = EstadoCompartimento.CerradoTrabado)
                entrega.fase == FaseEntrega.RetiroConfirmado && abierto ->
                    entrega.cambiarFase(FaseEntrega.ListaParaRegresar, ahoraMs)
                        .copy(compartimento = EstadoCompartimento.CerradoTrabado)
                else -> entrega
            }

            ComandoRobot.ConfirmarRetiro ->
                if (entrega.fase == FaseEntrega.RetirandoPaquete && abierto && entrega.conPaquete) {
                    entrega.retirarPaquete(horaMs)
                } else {
                    entrega
                }

            else -> entrega
        }
    }

    private fun leer(operacion: EstadoSimulador, ahoraMs: Long, horaMs: Long): TelemetriaRobot {
        val segundosTotal = (ahoraMs - inicioSimuladorMs) / INTERVALO_MS
        val base = TelemetriaRobot(
            latenciaMs = 24 + (segundosTotal % 7).toInt(),
            bateria = 86 - (segundosTotal / 6).coerceAtMost(6).toInt(),
            ultimaActualizacionMs = horaMs,
            ultimaEntrega = operacion.ultimaEntrega
        )
        val entrega = operacion.entrega
        val detencion = operacion.detencionSinEntrega
        val lectura = when {
            entrega != null -> entrega.aplicarALectura(base, ahoraMs)
            detencion != null -> base.copy(
                estado = EstadoOperativo.Detenido,
                ubicacion = detencion.ubicacion,
                mision = detencion.mision?.copy(etapa = "Detención operativa"),
                compartimento = detencion.compartimento,
                conPaquete = detencion.conPaquete
            )
            else -> leerPrueba(base, operacion.inicioPruebaMs, ahoraMs)
        }
        val respuesta = operacion.pendiente?.let {
            RespuestaOrden(it.orden, EstadoOrden.Pendiente, "Esperando la respuesta del G1.")
        } ?: operacion.ultimaRespuesta
        val alertas = if (lectura.compartimento == EstadoCompartimento.Abierto) {
            listOf(
                AlertaRobot(
                    "Compartimento abierto",
                    "Cerrá y trabá el compartimento antes de mover el G1.",
                    reconocida = operacion.compartimentoReconocido
                )
            )
        } else {
            emptyList()
        }
        return lectura.copy(
            respuestaOrden = respuesta,
            rechazoPreparado = operacion.rechazoPreparado,
            alertas = alertas
        )
    }

    private fun leerPrueba(base: TelemetriaRobot, inicioMs: Long?, ahoraMs: Long): TelemetriaRobot {
        val inicio = inicioMs ?: return base
        val segundosPrueba = (ahoraMs - inicio) / INTERVALO_MS
        return when {
            segundosPrueba < CARGA_SEGUNDOS -> base.copy(
                estado = EstadoOperativo.Preparando,
                mision = MisionRobot(destinoPrueba.nombre, "Cargando paquete", 0),
                compartimento = EstadoCompartimento.Abierto,
                conPaquete = segundosPrueba >= 2
            )
            segundosPrueba < LLEGADA_SEGUNDOS -> leyendoRecorrido(base, segundosPrueba.toInt())
            segundosPrueba < DURACION_PRUEBA_SEGUNDOS -> base.copy(
                estado = EstadoOperativo.EsperandoRetiro,
                mision = MisionRobot(destinoPrueba.nombre, "En la puerta de la oficina", 100),
                ubicacion = "Piso 2 · ${destinoPrueba.nombre}",
                conPaquete = true,
                esperaRestanteSegundos = ESPERA_RETIRO_SEGUNDOS - (segundosPrueba.toInt() - LLEGADA_SEGUNDOS)
            )
            else -> base
        }
    }

    private fun leyendoRecorrido(base: TelemetriaRobot, segundos: Int): TelemetriaRobot {
        val progreso = (segundos - CARGA_SEGUNDOS + 1) * 9
        val (etapa, ubicacion) = tramoDeIda(destinoPrueba, progreso)
        return base.copy(
            estado = EstadoOperativo.EnCamino,
            mision = MisionRobot(destinoPrueba.nombre, etapa, progreso),
            ubicacion = ubicacion,
            velocidad = 0.6,
            conPaquete = true
        )
    }
}

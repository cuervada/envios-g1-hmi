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
import edu.uade.enviosg1.model.LIMITE_BATERIA_INICIO
import edu.uade.enviosg1.model.MisionRobot
import edu.uade.enviosg1.model.OrdenRobot
import edu.uade.enviosg1.model.RespuestaOrden
import edu.uade.enviosg1.model.TelemetriaRobot
import edu.uade.enviosg1.model.TipoAlertaRobot
import edu.uade.enviosg1.model.motivoBloqueo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

private const val INTERVALO_MS = 1_000L
private const val RETARDO_RESPUESTA_MS = 1_000L
private const val LIMITE_RESPUESTA_MS = 3_000L
private const val AVANCE_PRUEBA_DESCONEXION_MS = 3_000L
private const val CARGA_SEGUNDOS = 5
private const val LLEGADA_SEGUNDOS = 15
private const val DURACION_PRUEBA_SEGUNDOS = 30
private val destinoPrueba = DestinoEntrega.Oficina204

private data class OrdenPendiente(
    val orden: OrdenRobot,
    val recibidaMs: Long,
    val horaRecepcionMs: Long,
    val rechazoForzado: Boolean,
    val confirmacionInterrumpida: Boolean = false,
    val alertasAReconocer: Set<TipoAlertaRobot> = emptySet()
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
    val alertasReconocidas: Set<TipoAlertaRobot> = emptySet(),
    val bateriaForzada: Boolean = false,
    val trayectoBloqueado: Boolean = false,
    val detencionSinEntrega: DetencionSinEntrega? = null,
    val lecturaAntesDelCorte: TelemetriaRobot? = null,
    val conexionRecuperada: Boolean = false
) {
    fun avanzarEntrega(ahoraMs: Long, horaMs: Long): EstadoSimulador {
        val anterior = entrega ?: return this
        return actualizarEntrega(anterior.avanzar(ahoraMs, horaMs))
    }

    fun actualizarEntrega(actual: EntregaSimulada): EstadoSimulador =
        if (actual == entrega) this else copy(entrega = actual, ultimaEntrega = actual.registro() ?: ultimaEntrega)

}

internal class SimuladorRobot(
    private val relojMonotonicoMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val horaActualMs: () -> Long = System::currentTimeMillis
) {
    private val inicioSimuladorMs = relojMonotonicoMs()
    private val estado = MutableStateFlow(EstadoSimulador())
    private val bloqueoEstado = Any()
    private var ultimaLecturaRecibida: TelemetriaRobot? = null

    private val intervalos = flow {
        while (true) {
            emit(Unit)
            delay(INTERVALO_MS)
        }
    }

    val telemetria: Flow<TelemetriaRobot> = intervalos.combine(estado) { _, _ ->
        synchronized(bloqueoEstado) {
            val ahora = relojMonotonicoMs()
            val hora = horaActualMs()
            val actual = avanzar(estado.value, ahora, hora)
            estado.value = actual
            val lectura = leer(actual, ahora, hora)
            if (lectura.conectado) ultimaLecturaRecibida = lectura
            lectura
        }
    }

    fun ejecutar(comando: ComandoRobot) {
        synchronized(bloqueoEstado) {
            val ahora = relojMonotonicoMs()
            val hora = horaActualMs()
            val actual = avanzar(estado.value, ahora, hora)
            val lectura = leer(actual, ahora, hora)
            estado.value = when {
                comando == ComandoRobot.CortarEnlace ->
                    if (lectura.conectado) cortarEnlace(actual, lectura, ultimaLecturaRecibida ?: lectura, ahora)
                    else actual

                comando == ComandoRobot.RecuperarEnlace -> recuperarEnlace(actual, ahora, hora)
                !lectura.conectado -> actual
                comando == ComandoRobot.AlternarBateriaBaja -> actual.copy(
                    bateriaForzada = !actual.bateriaForzada,
                    alertasReconocidas = actual.alertasReconocidas - TipoAlertaRobot.BateriaBaja
                )
                comando == ComandoRobot.AlternarObstaculo -> alternarObstaculo(actual, lectura, ahora)
                comando == ComandoRobot.ProbarDesconexion -> probarDesconexion(actual, lectura, ahora, hora)
                comando is OrdenRobot -> recibirOrden(actual, lectura, comando, ahora, hora)
                actual.pendiente != null -> actual
                else -> aplicarAccion(actual, lectura, comando, ahora, hora)
            }
        }
    }

    private fun cortarEnlace(
        estado: EstadoSimulador,
        lecturaActual: TelemetriaRobot,
        ultimaLectura: TelemetriaRobot,
        ahoraMs: Long
    ): EstadoSimulador {
        val entrega = estado.entrega
        val debePausar = entrega?.fase == FaseEntrega.EnCamino || entrega?.fase == FaseEntrega.Regresando
        val entregaTrasCorte = if (debePausar) {
            entrega.inmovilizar(FaseEntrega.Pausada, lecturaActual, ahoraMs)
        } else {
            entrega
        }
        return estado.copy(
            entrega = entregaTrasCorte,
            lecturaAntesDelCorte = ultimaLectura,
            conexionRecuperada = false,
            pendiente = estado.pendiente?.copy(confirmacionInterrumpida = true)
        )
    }

    private fun recuperarEnlace(estado: EstadoSimulador, ahoraMs: Long, horaMs: Long): EstadoSimulador {
        if (estado.lecturaAntesDelCorte == null) return estado
        val recuperado = estado.copy(lecturaAntesDelCorte = null, conexionRecuperada = true)
        ultimaLecturaRecibida = leer(recuperado, ahoraMs, horaMs)
        return recuperado
    }

    private fun alternarObstaculo(
        estado: EstadoSimulador,
        lectura: TelemetriaRobot,
        ahoraMs: Long
    ): EstadoSimulador {
        if (!lectura.puedeSimularObstaculo) return estado
        val seOcupaTrayecto = !estado.trayectoBloqueado
        val entrega = if (seOcupaTrayecto) {
            estado.entrega?.inmovilizar(FaseEntrega.Pausada, lectura, ahoraMs)
        } else {
            estado.entrega
        }
        return estado.copy(
            entrega = entrega,
            trayectoBloqueado = seOcupaTrayecto,
            alertasReconocidas = estado.alertasReconocidas - TipoAlertaRobot.Obstaculo
        )
    }

    private fun probarDesconexion(
        estado: EstadoSimulador,
        lectura: TelemetriaRobot,
        ahoraMs: Long,
        horaMs: Long
    ): EstadoSimulador {
        if (!lectura.puedeProbarDesconexion) return estado

        val escenario = if (lectura.estado == EstadoOperativo.Disponible) {
            estado.copy(
                entrega = EntregaSimulada(
                    datos = EntregaRobot(estado.siguienteNumero, destinoPrueba, FaseEntrega.EnCamino),
                    inicioFaseMs = ahoraMs - AVANCE_PRUEBA_DESCONEXION_MS,
                    conPaquete = true
                ),
                siguienteNumero = estado.siguienteNumero + 1,
                inicioPruebaMs = null,
                alertasReconocidas = estado.alertasReconocidas -
                    TipoAlertaRobot.CompartimentoAbierto - TipoAlertaRobot.RetiroVencido,
                ultimaRespuesta = null
            )
        } else {
            estado
        }
        val muestra = leer(escenario, ahoraMs, horaMs)
        val conPausa = recibirOrden(escenario, muestra, OrdenRobot.Pausar, ahoraMs, horaMs)
        return cortarEnlace(conPausa, muestra, muestra, ahoraMs)
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
        val alertasAReconocer = if (orden == OrdenRobot.ReconocerAlertas) {
            lectura.alertas.filterNot { it.reconocida }.map { it.tipo }.toSet()
        } else {
            emptySet()
        }
        return estado.copy(
            pendiente = OrdenPendiente(
                orden,
                ahoraMs,
                horaMs,
                forzar,
                alertasAReconocer = alertasAReconocer
            ),
            rechazoPreparado = estado.rechazoPreparado && !forzar,
            conexionRecuperada = false
        )
    }

    private fun avanzar(anterior: EstadoSimulador, ahoraMs: Long, horaMs: Long): EstadoSimulador {
        val pendiente = anterior.pendiente ?: return anterior.avanzarEntrega(ahoraMs, horaMs)
        if (pendiente.confirmacionInterrumpida) {
            val actual = anterior.avanzarEntrega(ahoraMs, horaMs)
            if (ahoraMs - pendiente.recibidaMs < LIMITE_RESPUESTA_MS) return actual
            return actual.copy(
                pendiente = null,
                ultimaRespuesta = RespuestaOrden(
                    pendiente.orden,
                    EstadoOrden.SinRespuesta,
                    "Venció la espera de confirmación del G1."
                )
            )
        }
        val instanteRespuesta = pendiente.recibidaMs + RETARDO_RESPUESTA_MS
        if (ahoraMs < instanteRespuesta) return anterior.avanzarEntrega(ahoraMs, horaMs)

        val horaRespuesta = pendiente.horaRecepcionMs + RETARDO_RESPUESTA_MS
        val actual = anterior.avanzarEntrega(instanteRespuesta, horaRespuesta).copy(pendiente = null)
        val lectura = leer(actual, instanteRespuesta, horaRespuesta)
        val motivo = when {
            pendiente.rechazoForzado -> "El simulador rechazó esta orden por la prueba de rechazo."
            pendiente.orden == OrdenRobot.ReconocerAlertas &&
                lectura.alertas.none { it.tipo in pendiente.alertasAReconocer } ->
                "Las alertas seleccionadas ya se resolvieron."
            else -> lectura.motivoBloqueo(pendiente.orden)
        }
        val resuelto = if (motivo != null) {
            actual.copy(ultimaRespuesta = RespuestaOrden(pendiente.orden, EstadoOrden.Rechazada, motivo))
        } else {
            confirmarOrden(actual, lectura, pendiente, instanteRespuesta, horaRespuesta)
        }
        return resuelto.avanzarEntrega(ahoraMs, horaMs)
    }

    private fun confirmarOrden(
        estado: EstadoSimulador,
        lectura: TelemetriaRobot,
        pendiente: OrdenPendiente,
        ahoraMs: Long,
        horaMs: Long
    ): EstadoSimulador {
        val orden = pendiente.orden
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
            OrdenRobot.Regresar -> estado.copy(entrega = checkNotNull(entrega).iniciarRegreso(lectura, ahoraMs, horaMs))
            OrdenRobot.Detener -> detener(estado, lectura, ahoraMs)
            OrdenRobot.ReconocerAlertas -> {
                val activas = lectura.alertas.map { it.tipo }.toSet()
                estado.copy(alertasReconocidas = estado.alertasReconocidas + (pendiente.alertasAReconocer intersect activas))
            }
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
        if (entrega?.fase == FaseEntrega.DescargaEnRecepcion) return estado
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
                    alertasReconocidas = estado.alertasReconocidas -
                    TipoAlertaRobot.CompartimentoAbierto - TipoAlertaRobot.RetiroVencido,
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
                estado.actualizarEntrega(actual).copy(
                    alertasReconocidas = if (nuevaApertura) {
                        estado.alertasReconocidas - TipoAlertaRobot.CompartimentoAbierto
                    } else {
                        estado.alertasReconocidas
                    }
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
            alertasReconocidas = estado.alertasReconocidas -
                    TipoAlertaRobot.CompartimentoAbierto - TipoAlertaRobot.RetiroVencido,
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
        val cierreMantieneFase = preparando || entrega.fase == FaseEntrega.Cancelada ||
            entrega.fase == FaseEntrega.Detenida || entrega.fase == FaseEntrega.RetiroVencido
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
                cierreMantieneFase && abierto ->
                    entrega.copy(compartimento = EstadoCompartimento.CerradoTrabado)
                entrega.fase == FaseEntrega.RetirandoPaquete && abierto ->
                    entrega.copy(
                        datos = entrega.datos.copy(fase = FaseEntrega.EsperandoRetiro),
                        compartimento = EstadoCompartimento.CerradoTrabado
                    )
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

            ComandoRobot.SimularPlazoVencido -> entrega.simularVencimiento(ahoraMs, horaMs)

            ComandoRobot.DescargarEnRecepcion ->
                if (entrega.fase == FaseEntrega.DescargaEnRecepcion) {
                    entrega.cambiarFase(FaseEntrega.Finalizada, ahoraMs)
                        .copy(conPaquete = false, compartimento = EstadoCompartimento.CerradoTrabado)
                } else {
                    entrega
                }

            else -> entrega
        }
    }

    private fun leer(operacion: EstadoSimulador, ahoraMs: Long, horaMs: Long): TelemetriaRobot {
        val respuesta = operacion.pendiente?.let {
            val detalle = if (it.confirmacionInterrumpida) {
                "El enlace se cortó antes de recibir la confirmación."
            } else {
                "Esperando la respuesta del G1."
            }
            RespuestaOrden(it.orden, EstadoOrden.Pendiente, detalle)
        } ?: operacion.ultimaRespuesta
        val guardada = operacion.lecturaAntesDelCorte
        if (guardada != null) {
            return guardada.copy(
                desactualizada = true,
                conexionRecuperada = false,
                respuestaOrden = respuesta,
                rechazoPreparado = operacion.rechazoPreparado
            )
        }
        val segundosTotal = (ahoraMs - inicioSimuladorMs) / INTERVALO_MS
        val bateriaEstimada = 86 - (segundosTotal / 6).coerceAtMost(6).toInt()
        val base = TelemetriaRobot(
            latenciaMs = 24 + (segundosTotal % 7).toInt(),
            bateria = if (operacion.bateriaForzada) LIMITE_BATERIA_INICIO else bateriaEstimada,
            ultimaActualizacionMs = horaMs,
            ultimaEntrega = operacion.ultimaEntrega,
            bateriaForzada = operacion.bateriaForzada,
            trayectoBloqueado = operacion.trayectoBloqueado
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
        return lectura.copy(
            respuestaOrden = respuesta,
            rechazoPreparado = operacion.rechazoPreparado,
            alertas = alertasActivas(lectura, operacion.alertasReconocidas),
            conexionRecuperada = operacion.conexionRecuperada
        )
    }

    private fun alertasActivas(
        lectura: TelemetriaRobot,
        reconocidas: Set<TipoAlertaRobot>
    ): List<AlertaRobot> = buildList {
        if (lectura.trayectoBloqueado) {
            val tipo = TipoAlertaRobot.Obstaculo
            add(AlertaRobot(
                tipo,
                "Obstáculo en el trayecto",
                "El G1 se detuvo. Retirá el obstáculo para habilitar el movimiento.",
                tipo in reconocidas
            ))
        }
        if (lectura.bateria <= LIMITE_BATERIA_INICIO) {
            val tipo = TipoAlertaRobot.BateriaBaja
            add(AlertaRobot(
                tipo,
                "Batería baja · ${lectura.bateria} %",
                "Se bloqueó el inicio de otra entrega. Solicitá el regreso si está habilitado.",
                tipo in reconocidas
            ))
        }
        if (lectura.entrega?.fase == FaseEntrega.RetiroVencido) {
            val tipo = TipoAlertaRobot.RetiroVencido
            add(AlertaRobot(
                tipo,
                "Paquete no retirado",
                "Venció el plazo. Solicitá el regreso cuando esté habilitado.",
                tipo in reconocidas
            ))
        }
        if (lectura.compartimento == EstadoCompartimento.Abierto) {
            val tipo = TipoAlertaRobot.CompartimentoAbierto
            add(AlertaRobot(
                tipo,
                "Compartimento abierto",
                "Cerrá y trabá el compartimento antes de mover el G1.",
                tipo in reconocidas
            ))
        }
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

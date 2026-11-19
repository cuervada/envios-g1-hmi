package edu.uade.enviosg1.data

import edu.uade.enviosg1.model.AlertaRobot
import edu.uade.enviosg1.model.ComandoRobot
import edu.uade.enviosg1.model.DestinoEntrega
import edu.uade.enviosg1.model.EntregaRegistrada
import edu.uade.enviosg1.model.EntregaRobot
import edu.uade.enviosg1.model.EstadoCompartimento
import edu.uade.enviosg1.model.EstadoOperativo
import edu.uade.enviosg1.model.FaseEntrega
import edu.uade.enviosg1.model.MisionRobot
import edu.uade.enviosg1.model.TelemetriaRobot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update

private const val INTERVALO_MS = 1_000L
private const val CARGA_SEGUNDOS = 5
private const val LLEGADA_SEGUNDOS = 15
private const val DURACION_PRUEBA_SEGUNDOS = 30
private const val ESPERA_RETIRO_SEGUNDOS = 4 * 60 + 30
private val destinoPrueba = DestinoEntrega.Oficina204

private data class EstadoSimulador(
    val inicioPruebaMs: Long? = null,
    val entrega: EntregaSimulada? = null,
    val ultimaEntrega: EntregaRegistrada? = null,
    val siguienteNumero: Int = 24
) {
    fun avanzar(ahoraMs: Long): EstadoSimulador {
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
        leer(operacion.avanzar(ahora), ahora, horaActualMs())
    }

    fun ejecutar(comando: ComandoRobot) {
        estado.update { anterior ->
            val ahora = relojMonotonicoMs()
            val hora = horaActualMs()
            val actual = anterior.avanzar(ahora)
            val lectura = leer(actual, ahora, hora)
            when (comando) {
                ComandoRobot.ProbarTelemetria -> {
                    if (lectura.estado == EstadoOperativo.Disponible) {
                        actual.copy(inicioPruebaMs = ahora, entrega = null)
                    } else {
                        actual
                    }
                }
                ComandoRobot.NuevaEntrega -> {
                    if (actual.entrega?.fase == FaseEntrega.Finalizada) {
                        actual.copy(entrega = null)
                    } else {
                        actual
                    }
                }
                is ComandoRobot.ElegirDestino -> elegirDestino(actual, lectura, comando.destino, ahora)
                else -> {
                    val entrega = actual.entrega
                    if (entrega != null && lectura.conectado) {
                        actual.copy(entrega = aplicarOrden(entrega, lectura, comando, ahora, hora))
                    } else {
                        actual
                    }
                }
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
            siguienteNumero = estado.siguienteNumero + 1
        )
    }

    private fun aplicarOrden(
        entrega: EntregaSimulada,
        lectura: TelemetriaRobot,
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

            ComandoRobot.IniciarMision ->
                if (lectura.puedeIniciarEntrega) entrega.cambiarFase(FaseEntrega.InicioPendiente, ahoraMs) else entrega

            ComandoRobot.ConfirmarRetiro ->
                if (entrega.fase == FaseEntrega.RetirandoPaquete && abierto && entrega.conPaquete) {
                    entrega.retirarPaquete(horaMs)
                } else {
                    entrega
                }

            ComandoRobot.SolicitarRegreso ->
                if (lectura.puedeRegresar) entrega.iniciarRegreso(ahoraMs, horaMs) else entrega

            ComandoRobot.ProbarTelemetria, ComandoRobot.NuevaEntrega, is ComandoRobot.ElegirDestino -> entrega
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
        if (entrega != null) return entrega.aplicarALectura(base, ahoraMs)
        val inicio = operacion.inicioPruebaMs ?: return base
        val segundosPrueba = (ahoraMs - inicio) / INTERVALO_MS
        return when {
            segundosPrueba < CARGA_SEGUNDOS -> leyendoCarga(base, segundosPrueba.toInt())
            segundosPrueba < LLEGADA_SEGUNDOS -> leyendoRecorrido(base, segundosPrueba.toInt())
            segundosPrueba < DURACION_PRUEBA_SEGUNDOS -> leyendoEspera(base, segundosPrueba.toInt())
            else -> base
        }
    }

    private fun leyendoCarga(base: TelemetriaRobot, segundos: Int) = base.copy(
        estado = EstadoOperativo.Preparando,
        mision = MisionRobot(destinoPrueba.nombre, "Cargando paquete", 0),
        compartimento = EstadoCompartimento.Abierto,
        conPaquete = segundos >= 2,
        alertas = listOf(
            AlertaRobot(
                "Compartimento abierto",
                "Cerrá y trabá el compartimento antes de iniciar una entrega."
            )
        )
    )

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

    private fun leyendoEspera(base: TelemetriaRobot, segundos: Int) = base.copy(
        estado = EstadoOperativo.EsperandoRetiro,
        mision = MisionRobot(destinoPrueba.nombre, "En la puerta de la oficina", 100),
        ubicacion = "Piso 2 · ${destinoPrueba.nombre}",
        conPaquete = true,
        esperaRestanteSegundos = ESPERA_RETIRO_SEGUNDOS - (segundos - LLEGADA_SEGUNDOS)
    )
}

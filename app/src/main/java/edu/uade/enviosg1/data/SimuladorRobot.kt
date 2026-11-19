package edu.uade.enviosg1.data

import edu.uade.enviosg1.model.AlertaRobot
import edu.uade.enviosg1.model.EstadoCompartimento
import edu.uade.enviosg1.model.EstadoOperativo
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
private const val DESTINO = "Oficina 204"

/**
 * Emite lecturas locales. La prueba reproduce estados del robot durante 30 segundos.
 */
internal class SimuladorRobot(
    private val relojMonotonicoMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val horaActualMs: () -> Long = System::currentTimeMillis
) {
    private val inicioSimuladorMs = relojMonotonicoMs()
    private val inicioPruebaMs = MutableStateFlow<Long?>(null)

    private val intervalos = flow {
        while (true) {
            emit(Unit)
            delay(INTERVALO_MS)
        }
    }

    val telemetria: Flow<TelemetriaRobot> = intervalos.combine(inicioPruebaMs) { _, inicio ->
        val ahora = relojMonotonicoMs()
        val segundosTotal = (ahora - inicioSimuladorMs) / INTERVALO_MS
        val base = TelemetriaRobot(
            latenciaMs = 24 + (segundosTotal % 7).toInt(),
            bateria = 86 - (segundosTotal / 6).coerceAtMost(6).toInt(),
            ultimaActualizacionMs = horaActualMs()
        )
        if (inicio == null) {
            base
        } else {
            val segundosPrueba = ((ahora - inicio) / INTERVALO_MS).toInt()
            when {
                segundosPrueba < CARGA_SEGUNDOS -> leyendoCarga(base, segundosPrueba)
                segundosPrueba < LLEGADA_SEGUNDOS -> leyendoRecorrido(base, segundosPrueba)
                segundosPrueba < DURACION_PRUEBA_SEGUNDOS -> leyendoEspera(base, segundosPrueba)
                else -> base
            }
        }
    }

    fun iniciarPrueba() {
        inicioPruebaMs.update { inicio ->
            val ahora = relojMonotonicoMs()
            if (inicio == null || ahora - inicio >= DURACION_PRUEBA_SEGUNDOS * INTERVALO_MS) {
                ahora
            } else {
                inicio
            }
        }
    }

    private fun leyendoCarga(base: TelemetriaRobot, segundos: Int) = base.copy(
        estado = EstadoOperativo.Preparando,
        mision = MisionRobot(DESTINO, "Cargando paquete", 0),
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
        val (etapa, ubicacion) = when {
            segundos < 8 -> "Saliendo de Recepción" to "Planta baja · Pasillo de Recepción"
            segundos < 11 -> "En ascensor" to "Ascensor · Hacia el piso 2"
            else -> "Atravesando Pasillo B" to "Piso 2 · Pasillo B"
        }
        return base.copy(
            estado = EstadoOperativo.EnCamino,
            mision = MisionRobot(DESTINO, etapa, (segundos - CARGA_SEGUNDOS + 1) * 9),
            ubicacion = ubicacion,
            velocidad = 0.6,
            conPaquete = true
        )
    }

    private fun leyendoEspera(base: TelemetriaRobot, segundos: Int) = base.copy(
        estado = EstadoOperativo.EsperandoRetiro,
        mision = MisionRobot(DESTINO, "En la puerta de la oficina", 100),
        ubicacion = "Piso 2 · $DESTINO",
        conPaquete = true,
        esperaRestanteSegundos = ESPERA_RETIRO_SEGUNDOS - (segundos - LLEGADA_SEGUNDOS)
    )
}

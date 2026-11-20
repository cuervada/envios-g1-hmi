package edu.uade.enviosg1.data

import android.database.sqlite.SQLiteException
import edu.uade.enviosg1.data.historial.HistorialDao
import edu.uade.enviosg1.data.historial.aEntidad
import edu.uade.enviosg1.model.ComandoRobot
import edu.uade.enviosg1.model.LecturaHistorial
import edu.uade.enviosg1.model.TelemetriaRobot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface RobotRepository {
    val telemetria: Flow<TelemetriaRobot>
    val historial: Flow<LecturaHistorial>

    suspend fun ejecutar(comando: ComandoRobot)
}

internal class RobotRepositorySimulado(
    private val simulador: SimuladorRobot,
    private val historialDao: HistorialDao
) : RobotRepository {
    private val guardadoEnCurso = Mutex()
    private val guardadoPendiente = MutableStateFlow(false)

    override val telemetria = simulador.telemetria.onEach { guardarPendientes() }

    override val historial = historialDao.observar()
        .map { filas -> LecturaHistorial(eventos = filas.map { it.aModelo() }, cargando = false) }
        .retryWhen { causa, _ ->
            if (causa !is SQLiteException) return@retryWhen false
            emit(LecturaHistorial(cargando = false, errorLectura = true))
            delay(2_000)
            true
        }
        .combine(guardadoPendiente) { lectura, pendiente -> lectura.copy(guardadoPendiente = pendiente) }

    override suspend fun ejecutar(comando: ComandoRobot) {
        simulador.ejecutar(comando)
        guardarPendientes()
    }

    private suspend fun guardarPendientes() {
        guardadoEnCurso.withLock {
            val pendientes = simulador.eventosPorGuardar()
            if (pendientes.isEmpty()) return
            try {
                historialDao.insertar(pendientes.map { it.aEntidad() })
                simulador.confirmarGuardado(pendientes.map { it.identificador }.toSet())
                guardadoPendiente.value = false
            } catch (_: SQLiteException) {
                guardadoPendiente.value = true
            }
        }
    }
}

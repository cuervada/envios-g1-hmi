package edu.uade.enviosg1.data

import edu.uade.enviosg1.model.ComandoRobot
import edu.uade.enviosg1.model.TelemetriaRobot
import kotlinx.coroutines.flow.Flow

internal interface RobotRepository {
    val telemetria: Flow<TelemetriaRobot>

    suspend fun ejecutar(comando: ComandoRobot)
}

internal class RobotRepositorySimulado(
    private val simulador: SimuladorRobot
) : RobotRepository {
    override val telemetria = simulador.telemetria

    override suspend fun ejecutar(comando: ComandoRobot) {
        simulador.ejecutar(comando)
    }
}

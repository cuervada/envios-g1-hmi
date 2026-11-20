package edu.uade.enviosg1.ui.robot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.uade.enviosg1.data.RobotRepository
import edu.uade.enviosg1.model.ComandoRobot
import edu.uade.enviosg1.model.LecturaHistorial
import edu.uade.enviosg1.model.TelemetriaRobot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class RobotViewModel(
    private val repositorio: RobotRepository
) : ViewModel() {
    val telemetria = repositorio.telemetria.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = TelemetriaRobot()
    )

    val historial = repositorio.historial.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = LecturaHistorial()
    )

    fun enviar(comando: ComandoRobot) {
        viewModelScope.launch {
            repositorio.ejecutar(comando)
        }
    }
}

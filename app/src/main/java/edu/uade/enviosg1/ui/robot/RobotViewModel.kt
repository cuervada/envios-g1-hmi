package edu.uade.enviosg1.ui.robot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.uade.enviosg1.data.RobotRepository
import edu.uade.enviosg1.model.ComandoRobot
import edu.uade.enviosg1.model.TelemetriaRobot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class RobotViewModel(
    private val repositorio: RobotRepository
) : ViewModel() {
    val telemetria = repositorio.telemetria.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = TelemetriaRobot()
    )

    fun enviar(comando: ComandoRobot) {
        viewModelScope.launch {
            repositorio.ejecutar(comando)
        }
    }
}

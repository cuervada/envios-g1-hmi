package edu.uade.enviosg1.ui.robot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import edu.uade.enviosg1.model.ComandoRobot
import edu.uade.enviosg1.model.EstadoOrden
import edu.uade.enviosg1.model.OrdenRobot
import edu.uade.enviosg1.model.RespuestaOrden
import edu.uade.enviosg1.model.TelemetriaRobot
import edu.uade.enviosg1.model.puedeEnviar
import edu.uade.enviosg1.ui.Aviso
import edu.uade.enviosg1.ui.BotonPrincipal
import edu.uade.enviosg1.ui.BotonSecundario
import edu.uade.enviosg1.ui.FilaDatos
import edu.uade.enviosg1.ui.Tarjeta
import edu.uade.enviosg1.ui.TipoAviso
import edu.uade.enviosg1.ui.TituloPantalla

@Composable
internal fun Controles(
    lectura: TelemetriaRobot,
    enviar: (ComandoRobot) -> Unit,
    alVolver: () -> Unit
) {
    var confirmarCancelacion by rememberSaveable { mutableStateOf(false) }
    val entrega = lectura.entrega
    val destino = entrega?.destino
    val subtitulo = if (destino == null) "G1-01 · Control operativo"
        else "Misión ${entrega.codigo} · ${destino.nombre}"

    TituloPantalla("Control de misión", subtitulo)
    TarjetaEstadoOperativo(lectura)
    lectura.mision?.let { mision ->
        Tarjeta {
            FilaDatos("Progreso de misión", "${mision.progreso} %")
        }
    }
    AvisoRespuesta(lectura.respuestaOrden)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BotonPrincipal(
            OrdenRobot.Detener.titulo,
            { enviar(OrdenRobot.Detener) },
            enabled = lectura.puedeEnviar(OrdenRobot.Detener),
            colores = ButtonDefaults.buttonColors(
                containerColor = TipoAviso.Error.texto,
                contentColor = Color.White
            )
        )
        BotonSecundario(
            OrdenRobot.Pausar.titulo,
            { enviar(OrdenRobot.Pausar) },
            enabled = lectura.puedeEnviar(OrdenRobot.Pausar)
        )
        BotonPrincipal(
            OrdenRobot.Reanudar.titulo,
            { enviar(OrdenRobot.Reanudar) },
            enabled = lectura.puedeEnviar(OrdenRobot.Reanudar)
        )
        BotonSecundario(
            OrdenRobot.Cancelar.titulo,
            { confirmarCancelacion = true },
            enabled = lectura.puedeEnviar(OrdenRobot.Cancelar)
        )
        BotonForzarRechazo(lectura, enviar)
        BotonSecundario("Volver", alVolver)
    }

    if (confirmarCancelacion) {
        AlertDialog(
            onDismissRequest = { confirmarCancelacion = false },
            title = { Text("¿Cancelar misión?") },
            text = { Text("El G1 quedará inmóvil en su ubicación actual.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmarCancelacion = false
                        enviar(OrdenRobot.Cancelar)
                    },
                    enabled = lectura.puedeEnviar(OrdenRobot.Cancelar)
                ) {
                    Text("Confirmar cancelación", color = TipoAviso.Error.texto)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmarCancelacion = false }) { Text("Volver") }
            }
        )
    }
}

@Composable
internal fun AvisoRespuesta(respuesta: RespuestaOrden?) {
    if (respuesta == null) return
    val tipo = when (respuesta.estado) {
        EstadoOrden.Pendiente -> TipoAviso.Advertencia
        EstadoOrden.Confirmada -> TipoAviso.Positivo
        EstadoOrden.Rechazada -> TipoAviso.Error
    }
    Aviso("${respuesta.estado.name} · ${respuesta.orden.titulo}", respuesta.detalle, tipo)
}

@Composable
internal fun BotonForzarRechazo(lectura: TelemetriaRobot, enviar: (ComandoRobot) -> Unit) {
    BotonSecundario(
        if (lectura.rechazoPreparado) "Quitar rechazo forzado" else "Forzar rechazo",
        { enviar(ComandoRobot.AlternarRechazo) },
        enabled = lectura.respuestaOrden?.estado != EstadoOrden.Pendiente
    )
}

package edu.uade.enviosg1.ui.robot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.uade.enviosg1.model.AlertaRobot
import edu.uade.enviosg1.model.EstadoCompartimento
import edu.uade.enviosg1.model.EstadoOperativo
import edu.uade.enviosg1.model.FaseEntrega
import edu.uade.enviosg1.model.TelemetriaRobot
import edu.uade.enviosg1.ui.Aviso
import edu.uade.enviosg1.ui.BotonPrincipal
import edu.uade.enviosg1.ui.BotonSecundario
import edu.uade.enviosg1.ui.FilaDatos
import edu.uade.enviosg1.ui.Tarjeta
import edu.uade.enviosg1.ui.TextoSecundario
import edu.uade.enviosg1.ui.TipoAviso
import edu.uade.enviosg1.ui.TituloPantalla
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val formatoLocal = Locale.forLanguageTag("es-AR")

@Composable
internal fun Panel(
    lectura: TelemetriaRobot,
    alPrepararEntrega: () -> Unit,
    alVerTelemetria: () -> Unit
) {
    val estado = lectura.estado.texto()
    TituloPantalla("Panel de supervisión", lectura.ubicacion)
    TarjetaEstadoOperativo(lectura)

    val filas = buildList {
        add("Compartimento" to lectura.compartimento.texto())
        add("Carga" to lectura.cargaTexto())
        lectura.esperaRestanteSegundos?.let { add("Tiempo de espera" to formatearEspera(it)) }
        add("Última actualización" to lectura.horaTexto())
    }
    Tarjeta {
        FilasLectura(filas)
    }

    val alerta = lectura.alertas.firstOrNull()
    val mision = lectura.mision
    when {
        alerta != null -> Aviso(alerta.titulo, alerta.detalle, TipoAviso.Advertencia)
        mision != null -> Aviso(estado, "${mision.etapa} · ${mision.progreso} %", TipoAviso.Positivo)
        else -> Aviso("Disponible", "Prepará una entrega para enviar un paquete.", TipoAviso.Positivo)
    }
    val fase = lectura.entrega?.fase
    val tieneEntregaActiva = fase != null && fase != FaseEntrega.Finalizada
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BotonPrincipal(
            if (tieneEntregaActiva) "Ver misión" else "Preparar entrega",
            alPrepararEntrega,
            enabled = tieneEntregaActiva || lectura.estado == EstadoOperativo.Disponible
        )
        BotonSecundario("Ver telemetría", alVerTelemetria)
    }
}

@Composable
internal fun TarjetaEstadoOperativo(lectura: TelemetriaRobot) {
    Tarjeta(altoMinimo = 113) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            TextoSecundario("Estado operativo", 12)
            Text(lectura.estado.texto(), fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        FilaDatos(lectura.ubicacion, "${lectura.bateriaTexto()} batería")
    }
}

@Composable
internal fun Telemetria(
    lectura: TelemetriaRobot,
    alProbar: () -> Unit,
    alVolver: () -> Unit
) {
    val mision = lectura.mision?.let {
        "${it.progreso} % · ${it.destino}\n${it.etapa}"
    } ?: "Sin misión"
    TituloPantalla("Telemetría", "G1-01 · Lectura actual")
    Tarjeta(
        modifier = Modifier.padding(bottom = 20.dp),
        radio = 14.dp,
        espacio = 11.dp,
        rellenoVertical = 12.dp
    ) {
        FilasLectura(
            listOf(
                "Conectividad" to lectura.conectividadTexto(),
                "Batería" to lectura.bateriaTexto(),
                "Estado operativo" to lectura.estado.texto(),
                "Misión y progreso" to mision,
                "Ubicación" to lectura.ubicacion,
                "Movimiento" to lectura.movimientoTexto(),
                "Alertas activas" to lectura.alertas.map { it.titulo }.joinToString(" · ").ifEmpty { "Ninguna" },
                "Última actualización" to lectura.horaTexto(),
                "Compartimento" to lectura.compartimento.texto(),
                "Carga" to lectura.cargaTexto(),
                "Tiempo de espera" to (lectura.esperaRestanteSegundos?.let(::formatearEspera) ?: "—")
            )
        )
    }
    BotonSecundario("Volver", alVolver)
    BotonSecundario("Probar telemetría (30 s)", alProbar, enabled = lectura.estado == EstadoOperativo.Disponible)
}

@Composable
internal fun Alertas(alertas: List<AlertaRobot>, alVolver: () -> Unit) {
    TituloPantalla("Alertas", "G1-01 · Estado e incidencias")
    if (alertas.isEmpty()) {
        Aviso("Sin alertas activas", "El enlace y el compartimento funcionan correctamente.", TipoAviso.Positivo)
    } else {
        alertas.forEach { alerta -> Aviso(alerta.titulo, alerta.detalle, TipoAviso.Advertencia) }
    }
    BotonSecundario("Volver al panel", alVolver)
}

@Composable
private fun FilasLectura(filas: List<Pair<String, String>>) {
    filas.forEachIndexed { indice, (etiqueta, valor) ->
        if (indice > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        FilaDatos(etiqueta, valor)
    }
}

internal fun TelemetriaRobot.bateriaTexto() = "$bateria %"

private fun TelemetriaRobot.conectividadTexto() =
    latenciaMs?.let { "Conectado · $it ms" } ?: "Desconectado"

private fun TelemetriaRobot.movimientoTexto(): String {
    val movimiento = if (velocidad == 0.0) "Inmóvil" else "En marcha"
    val valor = String.format(formatoLocal, "%.1f", velocidad)
    return "$movimiento · $valor m/s"
}

internal fun TelemetriaRobot.cargaTexto() = if (conPaquete) "Con paquete" else "Sin carga"

private fun TelemetriaRobot.horaTexto() = formatearHora(ultimaActualizacionMs)

internal fun formatearHora(horaMs: Long) =
    SimpleDateFormat("HH:mm:ss", formatoLocal).format(Date(horaMs))

internal fun formatearEspera(segundos: Int) =
    String.format(formatoLocal, "%02d:%02d", segundos / 60, segundos % 60)

private fun EstadoOperativo.texto() = when (this) {
    EstadoOperativo.Disponible -> "Disponible"
    EstadoOperativo.Preparando -> "Preparando"
    EstadoOperativo.EnCamino -> "En camino"
    EstadoOperativo.EsperandoRetiro -> "Esperando retiro"
    EstadoOperativo.RegresandoABase -> "Regresando a base"
}

internal fun EstadoCompartimento.texto() = when (this) {
    EstadoCompartimento.Abierto -> "Abierto"
    EstadoCompartimento.CerradoTrabado -> "Cerrado y trabado"
}

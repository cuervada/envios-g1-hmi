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
import edu.uade.enviosg1.model.ComandoRobot
import edu.uade.enviosg1.model.EstadoCompartimento
import edu.uade.enviosg1.model.EstadoOperativo
import edu.uade.enviosg1.model.EstadoOrden
import edu.uade.enviosg1.model.FaseEntrega
import edu.uade.enviosg1.model.OrdenRobot
import edu.uade.enviosg1.model.puedeEnviar
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
    alVerTelemetria: () -> Unit,
    alAbrirControles: () -> Unit
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

    val mision = lectura.mision
    when {
        lectura.alertas.isNotEmpty() -> lectura.alertas.forEach { AvisoAlerta(it) }
        lectura.entrega?.fase == FaseEntrega.DescargaEnRecepcion ->
            Aviso("Descarga pendiente", "Retirá el paquete en Recepción.", TipoAviso.Advertencia)
        lectura.estado == EstadoOperativo.Detenido -> Aviso(
            estado,
            mision?.etapa ?: "El G1 está inmóvil.",
            TipoAviso.Error
        )
        lectura.estado == EstadoOperativo.Pausado && mision != null -> Aviso(
            estado,
            "Progreso del recorrido · ${mision.progreso} %",
            TipoAviso.Advertencia
        )
        mision != null -> Aviso(estado, "${mision.etapa} · ${mision.progreso} %", TipoAviso.Positivo)
        else -> Aviso("Disponible", "Prepará una entrega para enviar un paquete.", TipoAviso.Positivo)
    }
    val fase = lectura.entrega?.fase
    val tieneEntregaActiva = fase != null && fase != FaseEntrega.Finalizada
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BotonPrincipal(
            if (tieneEntregaActiva) "Ver misión" else "Preparar entrega",
            alPrepararEntrega,
            enabled = tieneEntregaActiva || lectura.puedeElegirDestino
        )
        BotonSecundario("Ver telemetría", alVerTelemetria)
        BotonSecundario("Abrir controles", alAbrirControles)
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
    val bajada = if (lectura.desactualizada) "G1-01 · Última lectura recibida" else "G1-01 · Lectura actual"
    TituloPantalla("Telemetría", bajada)
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
    BotonSecundario(
        "Probar telemetría (30 s)",
        alProbar,
        enabled = lectura.conectado && lectura.estado == EstadoOperativo.Disponible &&
            lectura.respuestaOrden?.estado != EstadoOrden.Pendiente
    )
}

@Composable
internal fun Alertas(lectura: TelemetriaRobot, enviar: (ComandoRobot) -> Unit, alVolver: () -> Unit) {
    TituloPantalla("Alertas", "G1-01 · Estado e incidencias")
    if (lectura.alertas.isEmpty()) {
        if (lectura.desactualizada) {
            TextoSecundario("Sin alertas registradas en la última lectura.")
        } else {
            Aviso("Sin alertas activas", "El enlace y el compartimento funcionan correctamente.", TipoAviso.Positivo)
        }
    } else {
        lectura.alertas.forEach { AvisoAlerta(it) }
        BotonPrincipal(
            OrdenRobot.ReconocerAlertas.titulo,
            { enviar(OrdenRobot.ReconocerAlertas) },
            enabled = lectura.puedeEnviar(OrdenRobot.ReconocerAlertas)
        )
    }
    AvisoRespuesta(lectura.respuestaOrden?.takeIf { it.orden == OrdenRobot.ReconocerAlertas })
    BotonSecundario("Volver al panel", alVolver)
}

@Composable
internal fun AvisoAlerta(alerta: AlertaRobot) {
    val titulo = if (alerta.reconocida) "${alerta.titulo} · Reconocida" else alerta.titulo
    Aviso(titulo, alerta.detalle, TipoAviso.Advertencia)
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
    if (conectado) "Conectado · $latenciaMs ms" else "Desconectado"

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
    EstadoOperativo.Pausado -> "Pausado"
    EstadoOperativo.Detenido -> "Detenido"
}

internal fun EstadoCompartimento.texto() = when (this) {
    EstadoCompartimento.Abierto -> "Abierto"
    EstadoCompartimento.CerradoTrabado -> "Cerrado y trabado"
}

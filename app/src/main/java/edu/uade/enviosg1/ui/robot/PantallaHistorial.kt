package edu.uade.enviosg1.ui.robot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.uade.enviosg1.model.EventoHistorial
import edu.uade.enviosg1.model.LecturaHistorial
import edu.uade.enviosg1.ui.Aviso
import edu.uade.enviosg1.ui.BotonSecundario
import edu.uade.enviosg1.ui.Tarjeta
import edu.uade.enviosg1.ui.TextoSecundario
import edu.uade.enviosg1.ui.TipoAviso
import edu.uade.enviosg1.ui.TituloPantalla
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun Historial(
    lectura: LecturaHistorial,
    alVolver: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TituloPantalla("Historial", "Misiones y eventos registrados")
        }
        if (lectura.guardadoPendiente) {
            item {
                Aviso(
                    "Guardado pendiente",
                    "La app volverá a intentar guardar los últimos eventos.",
                    TipoAviso.Advertencia
                )
            }
        }
        when {
            lectura.cargando -> item { TextoSecundario("Cargando historial…") }
            lectura.errorLectura -> item {
                Aviso("Historial no disponible", "La app volverá a intentar leer los registros.", TipoAviso.Error)
            }
            lectura.eventos.isEmpty() -> item { TextoSecundario("Aún no hay eventos registrados.") }
        }

        items(lectura.eventos, key = { it.identificador }) { evento ->
            TarjetaEvento(evento)
        }
        item {
            BotonSecundario("Volver al panel", alVolver)
        }
    }
}

@Composable
private fun TarjetaEvento(evento: EventoHistorial) {
    val fecha = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.forLanguageTag("es-AR"))
        .format(Date(evento.horaMs))
    val categoria = listOfNotNull(evento.categoria.etiqueta, evento.codigoMision).joinToString(" · ")

    Tarjeta {
        TextoSecundario("$categoria · $fecha", 12)
        Text(
            evento.titulo,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        TextoSecundario(evento.detalle)
    }
}

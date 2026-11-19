package edu.uade.enviosg1.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val botonForma = RoundedCornerShape(11.dp)

internal enum class TipoAviso(val fondo: Color, val texto: Color) {
    Positivo(Color(0xFFDFF4E9), Color(0xFF08704A)),
    Advertencia(Color(0xFFFFF0CA), Color(0xFF765000)),
    Error(Color(0xFFFCE8E8), Color(0xFF9C2626))
}

@Composable
internal fun TituloPantalla(titulo: String, bajada: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            titulo,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        TextoSecundario(bajada)
    }
}

@Composable
internal fun TextoSecundario(texto: String, tamano: Int = 13) {
    Text(
        texto,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = tamano.sp,
        lineHeight = (tamano + 4).sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
internal fun FilaDatos(etiqueta: String, valor: String, colorValor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            etiqueta,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            lineHeight = 17.sp
        )
        Text(
            valor,
            modifier = Modifier.weight(1f),
            color = colorValor,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End
        )
    }
}

@Composable
internal fun Tarjeta(
    modifier: Modifier = Modifier,
    altoMinimo: Int = 0,
    radio: Dp = 17.dp,
    espacio: Dp = 8.dp,
    rellenoVertical: Dp = 14.dp,
    contenido: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = altoMinimo.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(radio),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = rellenoVertical),
            verticalArrangement = Arrangement.spacedBy(espacio),
            content = contenido
        )
    }
}

@Composable
internal fun Aviso(titulo: String, detalle: String, tipo: TipoAviso) {
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 92.dp),
        color = tipo.fondo,
        contentColor = tipo.texto,
        shape = RoundedCornerShape(13.dp)
    ) {
        Box {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(4.dp)
                    .height(68.dp)
                    .background(tipo.texto, RoundedCornerShape(2.dp))
            )
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(titulo, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
                Text(detalle, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
internal fun BotonPrincipal(
    texto: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    colores: ButtonColors = ButtonDefaults.buttonColors()
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        enabled = enabled,
        shape = botonForma,
        colors = colores
    ) {
        Text(texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun BotonSecundario(texto: String, onClick: () -> Unit = {}, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        enabled = enabled,
        shape = botonForma,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

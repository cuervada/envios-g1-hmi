package edu.uade.enviosg1.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ColoresEnviosG1 = lightColorScheme(
    primary = Violeta,
    onPrimary = Color.White,
    primaryContainer = VioletaClaro,
    onPrimaryContainer = Violeta,
    background = Fondo,
    onBackground = TextoPrincipal,
    surface = Color.White,
    onSurface = TextoPrincipal,
    onSurfaceVariant = TextoSecundario,
    outlineVariant = Borde
)

@Composable
fun EnviosG1Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColoresEnviosG1,
        typography = Typography,
        content = content
    )
}

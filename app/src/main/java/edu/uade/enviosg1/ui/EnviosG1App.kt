package edu.uade.enviosg1.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class Pantalla(val etiqueta: String) {
    Seleccion("Seleccioná el robot"),
    Panel("Panel"),
    Misiones("Misiones"),
    Alertas("Alertas"),
    Historial("Historial")
}

private val pestanas = listOf(Pantalla.Panel, Pantalla.Misiones, Pantalla.Alertas, Pantalla.Historial)
private val colorEncabezado = Color(0xFF18213B)
private val textoEncabezadoSecundario = Color(0xFFCBD4F2)
private val botonForma = RoundedCornerShape(11.dp)
private const val BASE = "Recepción · Planta baja"
private const val BATERIA_INICIAL = "86 %"

private enum class TipoAviso(val fondo: Color, val texto: Color) {
    Positivo(Color(0xFFDFF4E9), Color(0xFF08704A)),
    Advertencia(Color(0xFFFFF0CA), Color(0xFF765000))
}

@Composable
fun EnviosG1App() {
    var pantalla by rememberSaveable { mutableStateOf(Pantalla.Seleccion) }

    BackHandler(enabled = pantalla != Pantalla.Seleccion) {
        pantalla = if (pantalla == Pantalla.Panel) Pantalla.Seleccion else Pantalla.Panel
    }

    Scaffold(
        topBar = { Encabezado() },
        bottomBar = {
            if (pantalla == Pantalla.Seleccion) {
                PieSeleccion()
            } else {
                NavegacionInferior(pantalla) { pantalla = it }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { espacioBarras ->
        key(pantalla) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(espacioBarras)
                    .consumeWindowInsets(espacioBarras)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = if (pantalla == Pantalla.Seleccion) 30.dp else 22.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (pantalla) {
                    Pantalla.Seleccion -> SeleccionRobot { pantalla = Pantalla.Panel }
                    Pantalla.Panel -> Panel { pantalla = Pantalla.Misiones }
                    Pantalla.Misiones -> Misiones()
                    Pantalla.Alertas -> Alertas { pantalla = Pantalla.Panel }
                    Pantalla.Historial -> Historial { pantalla = Pantalla.Panel }
                }
            }
        }
    }
}

@Composable
private fun Encabezado() {
    val conectado = TipoAviso.Positivo
    Surface(color = colorEncabezado, contentColor = Color.White) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .heightIn(min = 84.dp)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MarcaG1(destacada = true)
            Column(Modifier.weight(1f)) {
                Text("Envíos G1", fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
                Text("G1-01 · Campus Buenos Aires", color = textoEncabezadoSecundario, fontSize = 11.sp, lineHeight = 14.sp)
            }
            Surface(color = conectado.fondo, contentColor = conectado.texto, shape = RoundedCornerShape(14.dp)) {
                Text(
                    "Conectado",
                    modifier = Modifier.widthIn(min = 113.dp).padding(horizontal = 10.dp, vertical = 5.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun MarcaG1(destacada: Boolean = false) {
    val colores = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(if (destacada) 46.dp else 44.dp)
            .background(
                color = if (destacada) colores.primary else colores.primaryContainer,
                shape = RoundedCornerShape(11.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "G1",
            color = if (destacada) colores.onPrimary else colores.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            fontSize = if (destacada) 19.sp else 17.sp
        )
    }
}

@Composable
private fun PieSeleccion() {
    Text(
        "Supervisión de entregas internas",
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = 48.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        fontSize = 12.sp
    )
}

@Composable
private fun NavegacionInferior(actual: Pantalla, alSeleccionar: (Pantalla) -> Unit) {
    val colores = MaterialTheme.colorScheme
    Surface(color = colores.surface) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = colores.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .padding(horizontal = 1.dp, vertical = 5.dp)
            ) {
                pestanas.forEach { pestana ->
                    val seleccionada = pestana == actual
                    val fondo = if (seleccionada) colores.primaryContainer else colores.surface
                    val texto = if (seleccionada) colores.primary else colores.onSurfaceVariant
                    val indicador = if (seleccionada) colores.primary else colores.outlineVariant
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 63.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(fondo)
                            .selectable(
                                selected = seleccionada,
                                role = Role.Tab,
                                onClick = { alSeleccionar(pestana) }
                            )
                            .padding(vertical = 9.dp, horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top)
                    ) {
                        Box(
                            Modifier
                                .width(22.dp)
                                .height(3.dp)
                                .background(indicador, RoundedCornerShape(2.dp))
                        )
                        Text(
                            pestana.etiqueta,
                            color = texto,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = if (seleccionada) FontWeight.Bold else FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeleccionRobot(alSeleccionar: () -> Unit) {
    TituloPantalla(Pantalla.Seleccion.etiqueta, "Logística interna · Campus Buenos Aires")
    Tarjeta(modifier = Modifier.padding(top = 18.dp), altoMinimo = 155) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MarcaG1()
            Column {
                Text("Unitree G1 EDU", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                TextoSecundario("G1-01 · Simulación", 12)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        FilaDatos("Disponible en Recepción", BATERIA_INICIAL, TipoAviso.Positivo.texto)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BotonPrincipal("Seleccionar G1-01", alSeleccionar)
        BotonSecundario("Probar desconexión", enabled = false)
    }
}

@Composable
private fun Panel(alPrepararEntrega: () -> Unit) {
    TituloPantalla("Panel de supervisión", BASE)
    Tarjeta(altoMinimo = 113) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            TextoSecundario("Estado operativo", 12)
            Text("Disponible", fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        FilaDatos(BASE, "$BATERIA_INICIAL batería")
    }
    Tarjeta {
        FilaDatos("Compartimento", "Cerrado y trabado")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        FilaDatos("Carga", "Sin carga")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        FilaDatos("Última actualización", "14:02:10")
    }
    Aviso("Disponible", "Prepará una entrega para enviar un paquete.", TipoAviso.Positivo)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BotonPrincipal("Preparar entrega", alPrepararEntrega)
        BotonSecundario("Ver telemetría", enabled = false)
    }
}

@Composable
private fun Misiones() {
    TituloPantalla("Elegí un destino", "Nueva misión · Desde Recepción")
    Tarjeta(altoMinimo = 119) {
        Text("Oficina 204", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        TextoSecundario("Piso 2 · Pasillo B")
        Text("Ruta disponible", color = TipoAviso.Positivo.texto, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
    BotonPrincipal("Elegir Oficina 204", onClick = {}, enabled = false)
}

@Composable
private fun Alertas(alVolver: () -> Unit) {
    TituloPantalla("Alertas", "G1-01 · Estado e incidencias")
    Aviso("Sin alertas activas", "El enlace y el compartimento funcionan correctamente.", TipoAviso.Positivo)
    BotonSecundario("Volver al panel", alVolver)
}

@Composable
private fun Historial(alVolver: () -> Unit) {
    TituloPantalla("Historial", "Misiones y eventos registrados")
    Aviso("G1-023 · Oficina 201", "No retirado · Regresó a Recepción a las 13:25.", TipoAviso.Advertencia)
    BotonSecundario("Volver al panel", alVolver)
}

@Composable
private fun TituloPantalla(titulo: String, bajada: String) {
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
private fun TextoSecundario(texto: String, tamano: Int = 13) {
    Text(
        texto,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = tamano.sp,
        lineHeight = (tamano + 4).sp,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun FilaDatos(etiqueta: String, valor: String, colorValor: Color = MaterialTheme.colorScheme.onSurface) {
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
private fun Tarjeta(
    modifier: Modifier = Modifier,
    altoMinimo: Int = 0,
    contenido: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = altoMinimo.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = contenido)
    }
}

@Composable
private fun Aviso(titulo: String, detalle: String, tipo: TipoAviso) {
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
private fun BotonPrincipal(texto: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        enabled = enabled,
        shape = botonForma
    ) {
        Text(texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BotonSecundario(texto: String, onClick: () -> Unit = {}, enabled: Boolean = true) {
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

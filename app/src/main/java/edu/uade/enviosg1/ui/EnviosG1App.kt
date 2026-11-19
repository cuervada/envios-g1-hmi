package edu.uade.enviosg1.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import edu.uade.enviosg1.model.ComandoRobot
import edu.uade.enviosg1.model.EntregaRegistrada
import edu.uade.enviosg1.model.EstadoOperativo
import edu.uade.enviosg1.model.TelemetriaRobot
import edu.uade.enviosg1.ui.robot.Alertas
import edu.uade.enviosg1.ui.robot.Controles
import edu.uade.enviosg1.ui.robot.Misiones
import edu.uade.enviosg1.ui.robot.Panel
import edu.uade.enviosg1.ui.robot.RobotViewModel
import edu.uade.enviosg1.ui.robot.Telemetria
import edu.uade.enviosg1.ui.robot.bateriaTexto
import edu.uade.enviosg1.ui.robot.formatearHora

private enum class Pantalla(val etiqueta: String) {
    Seleccion("Seleccioná el robot"),
    Panel("Panel"),
    Telemetria("Telemetría"),
    Control("Control"),
    Misiones("Misiones"),
    Alertas("Alertas"),
    Historial("Historial")
}

private val pestanas = listOf(Pantalla.Panel, Pantalla.Misiones, Pantalla.Alertas, Pantalla.Historial)
private val colorEncabezado = Color(0xFF18213B)
private val textoEncabezadoSecundario = Color(0xFFCBD4F2)

@Composable
internal fun EnviosG1App(robotViewModel: RobotViewModel) {
    val lectura by robotViewModel.telemetria.collectAsStateWithLifecycle()
    var pantalla by rememberSaveable { mutableStateOf(Pantalla.Seleccion) }
    var origenDetalle by rememberSaveable { mutableStateOf(Pantalla.Panel) }

    fun abrirDetalle(detalle: Pantalla, origen: Pantalla) {
        origenDetalle = origen
        pantalla = detalle
    }

    BackHandler(enabled = pantalla != Pantalla.Seleccion) {
        pantalla = when (pantalla) {
            Pantalla.Panel -> Pantalla.Seleccion
            Pantalla.Telemetria, Pantalla.Control -> origenDetalle
            else -> Pantalla.Panel
        }
    }

    Scaffold(
        topBar = { Encabezado(lectura.conectado) },
        bottomBar = {
            if (pantalla == Pantalla.Seleccion) {
                PieSeleccion()
            } else {
                val pestanaActual = when (pantalla) {
                    Pantalla.Telemetria, Pantalla.Control -> origenDetalle
                    else -> pantalla
                }
                NavegacionInferior(pestanaActual) { pantalla = it }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { espacioBarras ->
        val entregaEnMisiones = lectura.entrega.takeIf { pantalla == Pantalla.Misiones }
        key(pantalla, entregaEnMisiones?.fase, entregaEnMisiones?.destino) {
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
                    Pantalla.Seleccion -> SeleccionRobot(lectura) { pantalla = Pantalla.Panel }
                    Pantalla.Panel -> Panel(
                        lectura = lectura,
                        alPrepararEntrega = {
                            robotViewModel.enviar(ComandoRobot.NuevaEntrega)
                            pantalla = Pantalla.Misiones
                        },
                        alVerTelemetria = { abrirDetalle(Pantalla.Telemetria, Pantalla.Panel) },
                        alAbrirControles = { abrirDetalle(Pantalla.Control, Pantalla.Panel) }
                    )
                    Pantalla.Telemetria -> Telemetria(
                        lectura = lectura,
                        alProbar = { robotViewModel.enviar(ComandoRobot.ProbarTelemetria) },
                        alVolver = { pantalla = origenDetalle }
                    )
                    Pantalla.Control -> Controles(
                        lectura = lectura,
                        enviar = robotViewModel::enviar,
                        alVolver = { pantalla = origenDetalle }
                    )
                    Pantalla.Misiones -> Misiones(
                        lectura = lectura,
                        enviar = robotViewModel::enviar,
                        alVerTelemetria = { abrirDetalle(Pantalla.Telemetria, Pantalla.Misiones) },
                        alAbrirControles = { abrirDetalle(Pantalla.Control, Pantalla.Misiones) },
                        alVolverAlPanel = { pantalla = Pantalla.Panel }
                    )
                    Pantalla.Alertas -> Alertas(lectura, robotViewModel::enviar) { pantalla = Pantalla.Panel }
                    Pantalla.Historial -> Historial(lectura.ultimaEntrega) { pantalla = Pantalla.Panel }
                }
            }
        }
    }
}

@Composable
private fun Encabezado(conectado: Boolean) {
    val tipo = if (conectado) TipoAviso.Positivo else TipoAviso.Advertencia
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
            Surface(color = tipo.fondo, contentColor = tipo.texto, shape = RoundedCornerShape(14.dp)) {
                Text(
                    if (conectado) "Conectado" else "Desconectado",
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
private fun SeleccionRobot(lectura: TelemetriaRobot, alSeleccionar: () -> Unit) {
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
        val disponibilidad = if (lectura.estado == EstadoOperativo.Disponible) {
            "Disponible en Recepción"
        } else {
            lectura.ubicacion
        }
        FilaDatos(disponibilidad, lectura.bateriaTexto(), TipoAviso.Positivo.texto)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BotonPrincipal("Seleccionar G1-01", alSeleccionar)
        BotonSecundario("Probar desconexión", enabled = false)
    }
}

@Composable
private fun Historial(ultimaEntrega: EntregaRegistrada?, alVolver: () -> Unit) {
    TituloPantalla("Historial", "Misiones y eventos registrados")
    ultimaEntrega?.let { entrega ->
        val retiro = formatearHora(entrega.retiroConfirmadoMs)
        val regreso = formatearHora(entrega.regresoMs)
        Aviso(
            "${entrega.codigo} · ${entrega.destino.nombre}",
            "Retiro confirmado a las $retiro. Regresó a Recepción a las $regreso.",
            TipoAviso.Positivo
        )
    }
    Aviso("G1-023 · Oficina 201", "No retirado · Regresó a Recepción a las 13:25.", TipoAviso.Advertencia)
    BotonSecundario("Volver al panel", alVolver)
}

package edu.uade.enviosg1.ui.robot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.uade.enviosg1.model.ComandoRobot
import edu.uade.enviosg1.model.DestinoEntrega
import edu.uade.enviosg1.model.EntregaRobot
import edu.uade.enviosg1.model.EstadoCompartimento
import edu.uade.enviosg1.model.EstadoOrden
import edu.uade.enviosg1.model.FaseEntrega
import edu.uade.enviosg1.model.LIMITE_BATERIA_INICIO
import edu.uade.enviosg1.model.OrdenRobot
import edu.uade.enviosg1.model.TelemetriaRobot
import edu.uade.enviosg1.model.puedeEnviar
import edu.uade.enviosg1.ui.Aviso
import edu.uade.enviosg1.ui.BotonPrincipal
import edu.uade.enviosg1.ui.BotonSecundario
import edu.uade.enviosg1.ui.FilaDatos
import edu.uade.enviosg1.ui.Tarjeta
import edu.uade.enviosg1.ui.TextoSecundario
import edu.uade.enviosg1.ui.TipoAviso
import edu.uade.enviosg1.ui.TituloPantalla

@Composable
internal fun Misiones(
    lectura: TelemetriaRobot,
    enviar: (ComandoRobot) -> Unit,
    alVerTelemetria: () -> Unit,
    alAbrirControles: () -> Unit,
    alVolverAlPanel: () -> Unit
) {
    val entrega = lectura.entrega
    val destino = entrega?.destino
    if (entrega == null || destino == null) {
        SeleccionDestino(lectura.puedeElegirDestino, enviar)
        return
    }
    val subtitulo = "Misión ${entrega.codigo} · ${destino.nombre}"
    val respuesta = lectura.respuestaOrden
    if (respuesta?.orden == OrdenRobot.Iniciar && respuesta.estado == EstadoOrden.Pendiente) {
        TituloPantalla("Inicio de misión", subtitulo)
        TarjetaEstadoOperativo(lectura)
        ProgresoRecorrido(destino, 0)
        AvisoRespuesta(respuesta)
        BotonPrincipal("Iniciar misión", { enviar(OrdenRobot.Iniciar) }, enabled = false)
        BotonSecundario("Abrir controles", alAbrirControles)
        return
    }
    when (entrega.fase) {
        FaseEntrega.Preparando -> Preparacion(lectura, destino, enviar)
        FaseEntrega.EnCamino, FaseEntrega.Regresando -> Recorrido(
            lectura,
            destino,
            subtitulo,
            entrega.esRegreso,
            alVerTelemetria,
            alAbrirControles
        )

        FaseEntrega.EsperandoRetiro,
        FaseEntrega.RetirandoPaquete,
        FaseEntrega.RetiroConfirmado,
        FaseEntrega.ListaParaRegresar -> Retiro(lectura, entrega, destino, subtitulo, enviar)

        FaseEntrega.Pausada, FaseEntrega.Cancelada, FaseEntrega.Detenida -> {
            val titulo = when (entrega.fase) {
                FaseEntrega.Pausada -> "Misión en pausa"
                FaseEntrega.Cancelada -> "Misión cancelada"
                else -> "Detención operativa"
            }
            TituloPantalla(titulo, subtitulo)
            TarjetaEstadoOperativo(lectura)
            ProgresoRecorrido(destino, lectura.mision?.progreso ?: 0, entrega.esRegreso)
            AvisoRespuesta(respuesta)
            BotonSecundario("Ver telemetría", alVerTelemetria)
        }

        FaseEntrega.Finalizada -> {
            TituloPantalla("Entrega finalizada", subtitulo)
            TarjetaEstadoOperativo(lectura)
            Aviso("Entregada", "El paquete fue retirado y el G1 regresó a Recepción.", TipoAviso.Positivo)
            BotonPrincipal("Volver al panel", alVolverAlPanel)
        }
    }
    if (entrega.fase !in listOf(FaseEntrega.EnCamino, FaseEntrega.Regresando, FaseEntrega.Finalizada)) {
        BotonSecundario("Abrir controles", alAbrirControles)
    }
}

@Composable
private fun SeleccionDestino(habilitada: Boolean, enviar: (ComandoRobot) -> Unit) {
    val destino = DestinoEntrega.Oficina204
    TituloPantalla("Elegí un destino", "Nueva misión · Desde Recepción")
    Tarjeta(altoMinimo = 119) {
        Text(destino.nombre, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        TextoSecundario(destino.sector)
        Text(
            if (habilitada) "Ruta disponible" else "G1 en uso",
            color = if (habilitada) TipoAviso.Positivo.texto else TipoAviso.Advertencia.texto,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
    BotonPrincipal(
        "Elegir ${destino.nombre}",
        { enviar(ComandoRobot.ElegirDestino(destino)) },
        enabled = habilitada
    )
}

@Composable
private fun Preparacion(
    lectura: TelemetriaRobot,
    destino: DestinoEntrega,
    enviar: (ComandoRobot) -> Unit
) {
    TituloPantalla("Preparar entrega", "Destino · ${destino.nombre}")
    TarjetaEstadoOperativo(lectura)
    Tarjeta {
        Text("Verificación de carga", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        FilaDatos("Carga", lectura.cargaTexto())
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        FilaDatos("Compartimento", lectura.compartimento.texto())
    }
    if (lectura.puedeIniciarEntrega) {
        Aviso("Carga lista", "Paquete detectado. Compartimento cerrado y trabado.", TipoAviso.Positivo)
    } else {
        val detalle = when {
            !lectura.conectado -> "Recuperá el enlace con el G1."
            lectura.bateria <= LIMITE_BATERIA_INICIO -> "La batería debe superar el 20 %."
            !lectura.conPaquete && lectura.compartimento == EstadoCompartimento.Abierto ->
                "Simulá la carga del paquete."
            !lectura.conPaquete -> "Abrí el compartimento para cargar el paquete."
            else -> "Cerrá y trabá el compartimento."
        }
        Aviso("Preparación pendiente", detalle, TipoAviso.Advertencia)
    }

    AvisoRespuesta(lectura.respuestaOrden)
    val puedePrepararCarga = lectura.conectado && lectura.respuestaOrden?.estado != EstadoOrden.Pendiente
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BotonPrincipal("Iniciar misión", { enviar(OrdenRobot.Iniciar) }, lectura.puedeEnviar(OrdenRobot.Iniciar))
        when {
            lectura.compartimento == EstadoCompartimento.CerradoTrabado ->
                BotonSecundario("Abrir compartimento", { enviar(ComandoRobot.AbrirCompartimento) }, puedePrepararCarga)

            !lectura.conPaquete ->
                BotonSecundario("Simular carga del paquete", { enviar(ComandoRobot.CargarPaquete) }, puedePrepararCarga)

            else -> BotonSecundario("Cerrar y trabar", { enviar(ComandoRobot.CerrarYTrabar) }, puedePrepararCarga)
        }
        BotonSecundario("Cambiar destino", { enviar(ComandoRobot.CambiarDestino) }, puedePrepararCarga)
        BotonForzarRechazo(lectura, enviar)
    }
}

@Composable
private fun Recorrido(
    lectura: TelemetriaRobot,
    destino: DestinoEntrega,
    subtitulo: String,
    regresando: Boolean,
    alVerTelemetria: () -> Unit,
    alAbrirControles: () -> Unit
) {
    TituloPantalla(if (regresando) "Regresando a base" else destino.nombre, subtitulo)
    TarjetaEstadoOperativo(lectura)
    ProgresoRecorrido(destino, lectura.mision?.progreso ?: 0, regresando)
    lectura.mision?.let { TextoSecundario(it.etapa) }
    AvisoRespuesta(lectura.respuestaOrden)
    BotonSecundario("Abrir controles", alAbrirControles)
    BotonSecundario("Ver telemetría", alVerTelemetria)
}

@Composable
private fun Retiro(
    lectura: TelemetriaRobot,
    entrega: EntregaRobot,
    destino: DestinoEntrega,
    subtitulo: String,
    enviar: (ComandoRobot) -> Unit
) {
    val titulo = when (entrega.fase) {
        FaseEntrega.RetirandoPaquete -> "Retiro del paquete"
        FaseEntrega.RetiroConfirmado -> "Retiro confirmado"
        FaseEntrega.ListaParaRegresar -> "Listo para volver"
        else -> "Esperando retiro"
    }
    TituloPantalla(titulo, subtitulo)
    TarjetaEstadoOperativo(lectura)
    ProgresoRecorrido(destino, 100)
    AvisoRespuesta(lectura.respuestaOrden?.takeIf { it.orden == OrdenRobot.Regresar })
    val puedeOperar = lectura.conectado && lectura.respuestaOrden?.estado != EstadoOrden.Pendiente

    when (entrega.fase) {
        FaseEntrega.EsperandoRetiro -> {
            val tiempo = lectura.esperaRestanteSegundos?.let(::formatearEspera) ?: "—"
            Aviso("Tiempo para retirar · $tiempo", "El G1 espera en la puerta de la oficina.", TipoAviso.Advertencia)
            BotonPrincipal("Simular retiro del paquete", { enviar(ComandoRobot.AbrirCompartimento) }, puedeOperar)
        }
        FaseEntrega.RetirandoPaquete -> {
            Aviso("Compartimento abierto", "Retirá el paquete y confirmá el retiro.", TipoAviso.Advertencia)
            BotonPrincipal("Confirmar retiro", { enviar(ComandoRobot.ConfirmarRetiro) }, puedeOperar)
        }
        FaseEntrega.RetiroConfirmado -> {
            Aviso("Sin carga", "El sensor de carga indica que el compartimento está vacío. Cerrá y trabá para volver.", TipoAviso.Positivo)
            BotonPrincipal("Cerrar y trabar", { enviar(ComandoRobot.CerrarYTrabar) }, puedeOperar)
        }
        FaseEntrega.ListaParaRegresar -> {
            Aviso("Cerrado y trabado", "El supervisor puede solicitar el regreso.", TipoAviso.Positivo)
            BotonPrincipal(
                "Solicitar regreso a punto seguro",
                { enviar(OrdenRobot.Regresar) },
                enabled = lectura.puedeEnviar(OrdenRobot.Regresar)
            )
        }
        else -> Unit
    }
}

@Composable
private fun ProgresoRecorrido(destino: DestinoEntrega, porcentaje: Int, regreso: Boolean = false) {
    val colores = MaterialTheme.colorScheme
    val progreso = porcentaje / 100f
    val inicio = if (regreso) destino.nombre else "Recepción"
    val fin = if (regreso) "Recepción" else destino.nombre
    Tarjeta(altoMinimo = 97, radio = 14.dp, rellenoVertical = 10.dp) {
        FilaDatos("Progreso de misión", "$porcentaje %")
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .progressSemantics(progreso)
                .background(colores.outlineVariant, RoundedCornerShape(3.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progreso)
                    .height(6.dp)
                    .background(colores.primary, RoundedCornerShape(3.dp))
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MarcaRecorrido(inicio, true, Alignment.Start)
            MarcaRecorrido("Ascensor", porcentaje >= 36, Alignment.CenterHorizontally)
            MarcaRecorrido(fin, porcentaje == 100, Alignment.End)
        }
    }
}

@Composable
private fun MarcaRecorrido(nombre: String, alcanzada: Boolean, alineacion: Alignment.Horizontal) {
    val colores = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = alineacion,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(if (alcanzada) colores.primary else colores.outlineVariant, CircleShape)
        )
        Text(nombre, fontSize = 11.sp, lineHeight = 14.sp, textAlign = TextAlign.Center)
    }
}

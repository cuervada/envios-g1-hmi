package edu.uade.enviosg1.model

internal sealed interface ComandoRobot {
    data object ProbarTelemetria : ComandoRobot
    data object NuevaEntrega : ComandoRobot
    data class ElegirDestino(val destino: DestinoEntrega) : ComandoRobot
    data object CambiarDestino : ComandoRobot
    data object AbrirCompartimento : ComandoRobot
    data object CargarPaquete : ComandoRobot
    data object CerrarYTrabar : ComandoRobot
    data object ConfirmarRetiro : ComandoRobot
    data object AlternarRechazo : ComandoRobot
    data object CortarEnlace : ComandoRobot
    data object RecuperarEnlace : ComandoRobot
    data object ProbarDesconexion : ComandoRobot
    data object AlternarBateriaBaja : ComandoRobot
    data object AlternarObstaculo : ComandoRobot
    data object DescargarEnRecepcion : ComandoRobot
}

internal enum class OrdenRobot(val titulo: String) : ComandoRobot {
    Iniciar("Iniciar misión"),
    Pausar("Pausar misión"),
    Reanudar("Reanudar misión"),
    Cancelar("Cancelar misión"),
    Regresar("Solicitar regreso"),
    Detener("Solicitar detención operativa"),
    ReconocerAlertas("Reconocer alertas")
}

internal enum class EstadoOrden(val etiqueta: String) {
    Pendiente("Pendiente"),
    Confirmada("Confirmada"),
    Rechazada("Rechazada"),
    SinRespuesta("Sin respuesta")
}

internal data class RespuestaOrden(
    val orden: OrdenRobot,
    val estado: EstadoOrden,
    val detalle: String
)

internal fun TelemetriaRobot.puedeEnviar(orden: OrdenRobot): Boolean =
    (orden == OrdenRobot.Detener || respuestaOrden?.estado != EstadoOrden.Pendiente) &&
        motivoBloqueo(orden) == null

internal fun TelemetriaRobot.motivoBloqueo(orden: OrdenRobot): String? {
    if (!conectado) return "El enlace con el G1 está desconectado."
    val fase = entrega?.fase
    val cerradaYTrabada = compartimento == EstadoCompartimento.CerradoTrabado

    val habilitada = when (orden) {
        OrdenRobot.Iniciar -> puedeIniciarEntrega
        OrdenRobot.Pausar -> fase == FaseEntrega.EnCamino || fase == FaseEntrega.Regresando
        OrdenRobot.Reanudar -> fase == FaseEntrega.Pausada && cerradaYTrabada &&
            !trayectoBloqueado && bateria > 0
        OrdenRobot.Cancelar -> fase != null && estado in estadosCancelables
        OrdenRobot.Regresar -> puedeRegresar
        OrdenRobot.Detener -> true
        OrdenRobot.ReconocerAlertas -> alertas.any { !it.reconocida }
    }
    if (habilitada) return null
    return when (orden) {
        OrdenRobot.Iniciar -> "Verificá el destino, la batería, el paquete y la traba del compartimento."
        OrdenRobot.Pausar -> "El G1 debe estar en marcha para pausar la misión."
        OrdenRobot.Reanudar -> when {
            trayectoBloqueado -> "Retirá el obstáculo para reanudar."
            bateria <= 0 -> "El G1 necesita batería para moverse."
            else -> "La misión debe estar en pausa y el compartimento cerrado y trabado."
        }
        OrdenRobot.Cancelar -> "El G1 debe estar en camino, en pausa o esperando el retiro."
        OrdenRobot.Regresar -> when {
            trayectoBloqueado -> "Retirá el obstáculo para habilitar el regreso."
            bateria <= 0 -> "El G1 necesita batería para regresar."
            !cerradaYTrabada -> "Cerrá y trabá el compartimento para regresar."
            else -> "El G1 debe estar fuera de Recepción y tener un trayecto de regreso disponible."
        }
        OrdenRobot.Detener -> null
        OrdenRobot.ReconocerAlertas -> "Ya no hay alertas activas pendientes de reconocer."
    }
}

private val estadosCancelables = setOf(
    EstadoOperativo.EnCamino,
    EstadoOperativo.Pausado,
    EstadoOperativo.EsperandoRetiro
)

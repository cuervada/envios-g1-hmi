package edu.uade.enviosg1.model

internal const val LIMITE_BATERIA_INICIO = 20

internal enum class DestinoEntrega(val nombre: String, val sector: String) {
    Oficina204("Oficina 204", "Piso 2 · Pasillo B")
}

internal enum class FaseEntrega {
    Preparando,
    InicioPendiente,
    EnCamino,
    EsperandoRetiro,
    RetirandoPaquete,
    RetiroConfirmado,
    ListaParaRegresar,
    Regresando,
    Finalizada
}

internal data class EntregaRobot(
    val numero: Int,
    val destino: DestinoEntrega?,
    val fase: FaseEntrega = FaseEntrega.Preparando,
    val retiroConfirmadoMs: Long? = null
) {
    val codigo: String
        get() = "G1-" + numero.toString().padStart(3, '0')
}

internal data class EntregaRegistrada(
    val codigo: String,
    val destino: DestinoEntrega,
    val retiroConfirmadoMs: Long,
    val regresoMs: Long
)

internal sealed interface ComandoRobot {
    data object ProbarTelemetria : ComandoRobot
    data object NuevaEntrega : ComandoRobot
    data class ElegirDestino(val destino: DestinoEntrega) : ComandoRobot
    data object CambiarDestino : ComandoRobot
    data object AbrirCompartimento : ComandoRobot
    data object CargarPaquete : ComandoRobot
    data object CerrarYTrabar : ComandoRobot
    data object IniciarMision : ComandoRobot
    data object ConfirmarRetiro : ComandoRobot
    data object SolicitarRegreso : ComandoRobot
}

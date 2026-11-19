package edu.uade.enviosg1.model

internal const val LIMITE_BATERIA_INICIO = 20

internal enum class DestinoEntrega(val nombre: String, val sector: String) {
    Oficina204("Oficina 204", "Piso 2 · Pasillo B")
}

internal enum class FaseEntrega {
    Preparando,
    EnCamino,
    EsperandoRetiro,
    RetirandoPaquete,
    RetiroConfirmado,
    RetiroVencido,
    ListaParaRegresar,
    Regresando,
    Pausada,
    Cancelada,
    Detenida,
    DescargaEnRecepcion,
    Finalizada
}

internal data class EntregaRobot(
    val numero: Int,
    val destino: DestinoEntrega?,
    val fase: FaseEntrega = FaseEntrega.Preparando,
    val retiroConfirmadoMs: Long? = null,
    val esRegreso: Boolean = false,
    val retiroVencidoMs: Long? = null
) {
    val codigo: String
        get() = "G1-" + numero.toString().padStart(3, '0')
}

internal enum class ResultadoEntrega(val etiqueta: String) {
    Entregada("Entregada"),
    NoRetirado("No retirado")
}

internal data class EntregaRegistrada(
    val codigo: String,
    val destino: DestinoEntrega,
    val resultado: ResultadoEntrega,
    val horaResultadoMs: Long,
    val regresoMs: Long?
)

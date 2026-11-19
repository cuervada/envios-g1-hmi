package edu.uade.enviosg1.model

internal enum class EstadoOperativo {
    Disponible,
    Preparando,
    EnCamino,
    EsperandoRetiro,
    RegresandoABase,
    Pausado,
    Detenido
}

internal enum class EstadoCompartimento {
    Abierto,
    CerradoTrabado
}

internal data class MisionRobot(
    val destino: String,
    val etapa: String,
    val progreso: Int
)

internal data class AlertaRobot(
    val titulo: String,
    val detalle: String,
    val reconocida: Boolean = false
)

internal data class TelemetriaRobot(
    val latenciaMs: Int? = 24,
    val bateria: Int = 86,
    val estado: EstadoOperativo = EstadoOperativo.Disponible,
    val mision: MisionRobot? = null,
    val ubicacion: String = "Recepción · Planta baja",
    val velocidad: Double = 0.0,
    val alertas: List<AlertaRobot> = emptyList(),
    val ultimaActualizacionMs: Long = System.currentTimeMillis(),
    val compartimento: EstadoCompartimento = EstadoCompartimento.CerradoTrabado,
    val conPaquete: Boolean = false,
    val esperaRestanteSegundos: Int? = null,
    val entrega: EntregaRobot? = null,
    val ultimaEntrega: EntregaRegistrada? = null,
    val respuestaOrden: RespuestaOrden? = null,
    val rechazoPreparado: Boolean = false,
    val desactualizada: Boolean = false,
    val conexionRecuperada: Boolean = false
) {
    val conectado: Boolean
        get() = latenciaMs != null && !desactualizada

    val puedeProbarDesconexion: Boolean
        get() = conectado && respuestaOrden?.estado != EstadoOrden.Pendiente &&
            (estado == EstadoOperativo.Disponible || puedeEnviar(OrdenRobot.Pausar))

    val puedeElegirDestino: Boolean
        get() = conectado && respuestaOrden?.estado != EstadoOrden.Pendiente &&
            (estado == EstadoOperativo.Disponible || entrega?.fase == FaseEntrega.Preparando)

    val puedeIniciarEntrega: Boolean
        get() = conectado &&
            bateria > LIMITE_BATERIA_INICIO &&
            entrega?.fase == FaseEntrega.Preparando &&
            entrega.destino != null &&
            conPaquete &&
            compartimento == EstadoCompartimento.CerradoTrabado

    val puedeRegresar: Boolean
        get() = conectado &&
            bateria > 0 &&
            entrega?.fase == FaseEntrega.ListaParaRegresar &&
            entrega.retiroConfirmadoMs != null &&
            !conPaquete &&
            compartimento == EstadoCompartimento.CerradoTrabado
}

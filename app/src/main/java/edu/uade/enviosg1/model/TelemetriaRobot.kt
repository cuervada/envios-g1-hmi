package edu.uade.enviosg1.model

internal enum class EstadoOperativo {
    Disponible,
    Preparando,
    EnCamino,
    EsperandoRetiro
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
    val detalle: String
)

internal enum class ComandoRobot {
    ProbarTelemetria
}

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
    val esperaRestanteSegundos: Int? = null
) {
    val conectado: Boolean
        get() = latenciaMs != null
}

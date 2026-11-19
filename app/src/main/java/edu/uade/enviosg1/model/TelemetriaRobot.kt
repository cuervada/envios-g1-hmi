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

internal enum class TipoAlertaRobot {
    Obstaculo,
    BateriaBaja,
    CompartimentoAbierto,
    RetiroVencido
}

internal data class AlertaRobot(
    val tipo: TipoAlertaRobot,
    val titulo: String,
    val detalle: String,
    val reconocida: Boolean = false
)

internal data class TelemetriaRobot(
    val latenciaMs: Int? = 24,
    val bateria: Int = 86,
    val estado: EstadoOperativo = EstadoOperativo.Disponible,
    val mision: MisionRobot? = null,
    val ubicacion: String = UBICACION_RECEPCION,
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
    val conexionRecuperada: Boolean = false,
    val trayectoBloqueado: Boolean = false,
    val bateriaForzada: Boolean = false
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

    val puedeSimularObstaculo: Boolean
        get() = conectado && (
            trayectoBloqueado || entrega?.fase == FaseEntrega.EnCamino || entrega?.fase == FaseEntrega.Regresando
        )

    val puedeSimularVencimiento: Boolean
        get() = conectado && respuestaOrden?.estado != EstadoOrden.Pendiente &&
            conPaquete && entrega?.fase.let {
                it == FaseEntrega.EsperandoRetiro || it == FaseEntrega.RetirandoPaquete
            }

    val puedeRegresar: Boolean
        get() = conectado &&
            !trayectoBloqueado &&
            bateria > 0 &&
            entrega != null &&
            (!entrega.esRegreso || entrega.fase == FaseEntrega.Cancelada || entrega.fase == FaseEntrega.Detenida) &&
            entrega.fase in fasesConRegreso &&
            ubicacion != UBICACION_RECEPCION &&
            compartimento == EstadoCompartimento.CerradoTrabado
}

internal const val UBICACION_RECEPCION = "Recepción · Planta baja"

private val fasesConRegreso = setOf(
    FaseEntrega.EnCamino,
    FaseEntrega.EsperandoRetiro,
    FaseEntrega.RetiroVencido,
    FaseEntrega.ListaParaRegresar,
    FaseEntrega.Pausada,
    FaseEntrega.Cancelada,
    FaseEntrega.Detenida
)

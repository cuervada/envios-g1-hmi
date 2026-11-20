package edu.uade.enviosg1.model

internal enum class CategoriaHistorial(val etiqueta: String) {
    Mision("Misión"),
    Orden("Orden"),
    Contingencia("Contingencia")
}

internal data class EventoHistorial(
    val identificador: String,
    val horaMs: Long,
    val categoria: CategoriaHistorial,
    val codigoMision: String?,
    val titulo: String,
    val detalle: String
)

internal data class LecturaHistorial(
    val eventos: List<EventoHistorial> = emptyList(),
    val cargando: Boolean = true,
    val errorLectura: Boolean = false,
    val guardadoPendiente: Boolean = false
)

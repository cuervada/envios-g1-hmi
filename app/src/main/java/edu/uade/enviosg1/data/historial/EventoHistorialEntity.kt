package edu.uade.enviosg1.data.historial

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import edu.uade.enviosg1.model.CategoriaHistorial
import edu.uade.enviosg1.model.EventoHistorial

@Entity(
    tableName = "eventos_historial",
    indices = [
        Index(value = ["identificador"], unique = true),
        Index(value = ["horaMs"])
    ]
)
internal data class EventoHistorialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val identificador: String,
    val horaMs: Long,
    val categoria: String,
    val codigoMision: String?,
    val titulo: String,
    val detalle: String
) {
    fun aModelo() = EventoHistorial(
        identificador,
        horaMs,
        CategoriaHistorial.valueOf(categoria),
        codigoMision,
        titulo,
        detalle
    )
}

internal fun EventoHistorial.aEntidad() = EventoHistorialEntity(
    identificador = identificador,
    horaMs = horaMs,
    categoria = categoria.name,
    codigoMision = codigoMision,
    titulo = titulo,
    detalle = detalle
)

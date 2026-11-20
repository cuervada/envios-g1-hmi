package edu.uade.enviosg1.data.historial

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface HistorialDao {
    @Query("SELECT * FROM eventos_historial ORDER BY horaMs DESC, id DESC")
    fun observar(): Flow<List<EventoHistorialEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertar(eventos: List<EventoHistorialEntity>)
}

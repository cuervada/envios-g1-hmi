package edu.uade.enviosg1.data.historial

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [EventoHistorialEntity::class], version = 1, exportSchema = false)
internal abstract class HistorialDatabase : RoomDatabase() {
    abstract fun historialDao(): HistorialDao

    companion object {
        @Volatile
        private var instancia: HistorialDatabase? = null

        fun obtener(context: Context): HistorialDatabase =
            instancia ?: synchronized(this) {
                instancia ?: Room.databaseBuilder(
                    context.applicationContext,
                    HistorialDatabase::class.java,
                    "enviosg1-historial.db"
                ).build().also { instancia = it }
            }
    }
}

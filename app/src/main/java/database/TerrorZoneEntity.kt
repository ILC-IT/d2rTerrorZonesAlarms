package database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "terror_zones",
    indices = [Index(value = ["timestampHour", "zoneName"], unique = true)]
)
data class TerrorZoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0, // ID autogenerado
    val timestamp: Long, // Tiempo en epoch ms
    val zoneName: String, // Nombre de la zona
    val timestampHour: Long // Timestamp con dia, mes, año, hora y lo demás a cero
)

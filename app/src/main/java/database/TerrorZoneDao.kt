package database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TerrorZoneDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertZone(terrorZone: TerrorZoneEntity)

    @Query("SELECT COUNT(*) FROM terror_zones")
    suspend fun countZones(): Int

    @Query("SELECT * FROM terror_zones WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    suspend fun getZonesBetween(startTime: Long, endTime: Long): List<TerrorZoneEntity>

    @Query("SELECT * FROM terror_zones ORDER BY timestamp ASC LIMIT 1")
    suspend fun getOldestZone(): TerrorZoneEntity?

    @Query("SELECT * FROM terror_zones WHERE timestamp = :timestamp AND zoneName = :zoneName LIMIT 1")
    suspend fun getZoneByTimeAndName(timestamp: Long, zoneName: String): TerrorZoneEntity?

    @Delete
    suspend fun deleteZone(terrorZone: TerrorZoneEntity)

    @Query("DELETE FROM terror_zones WHERE timestamp < :timeLimit")
    suspend fun deleteOldZones(timeLimit: Long)
}
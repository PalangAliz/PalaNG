package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ServerConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerConfigDao {
    @Query("SELECT * FROM server_configs ORDER BY sortOrder ASC, createdAt DESC")
    fun observeAll(): Flow<List<ServerConfig>>

    @Query("SELECT * FROM server_configs ORDER BY sortOrder ASC, createdAt DESC")
    suspend fun getAll(): List<ServerConfig>

    @Query("SELECT * FROM server_configs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ServerConfig?

    @Query("SELECT * FROM server_configs WHERE subscriptionId = :subscriptionId ORDER BY sortOrder ASC, createdAt DESC")
    suspend fun getBySubscription(subscriptionId: String): List<ServerConfig>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(configs: List<ServerConfig>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: ServerConfig)

    @Update
    suspend fun update(config: ServerConfig)

    @Query("UPDATE server_configs SET latencyMs = :latencyMs WHERE id = :id")
    suspend fun updateLatency(id: String, latencyMs: Long)

    @Query("UPDATE server_configs SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int)

    @Query("DELETE FROM server_configs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM server_configs WHERE subscriptionId = :subscriptionId")
    suspend fun deleteBySubscription(subscriptionId: String)

    @Query("DELETE FROM server_configs WHERE latencyMs < 0 AND latencyMs != -2")
    suspend fun deleteInvalidTested()

    @Query("DELETE FROM server_configs")
    suspend fun deleteAll()
}

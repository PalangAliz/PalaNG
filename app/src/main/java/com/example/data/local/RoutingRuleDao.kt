package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.RoutingRule
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutingRuleDao {
    @Query("SELECT * FROM routing_rules ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<RoutingRule>>

    @Query("SELECT * FROM routing_rules ORDER BY sortOrder ASC")
    suspend fun getAll(): List<RoutingRule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<RoutingRule>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: RoutingRule)

    @Update
    suspend fun update(rule: RoutingRule)

    @Query("DELETE FROM routing_rules WHERE id = :id")
    suspend fun deleteById(id: String)
}

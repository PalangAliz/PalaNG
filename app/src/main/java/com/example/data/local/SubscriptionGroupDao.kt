package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.SubscriptionGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionGroupDao {
    @Query("SELECT * FROM subscription_groups ORDER BY remarks ASC")
    fun observeAll(): Flow<List<SubscriptionGroup>>

    @Query("SELECT * FROM subscription_groups ORDER BY remarks ASC")
    suspend fun getAll(): List<SubscriptionGroup>

    @Query("SELECT * FROM subscription_groups WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SubscriptionGroup?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: SubscriptionGroup)

    @Update
    suspend fun update(group: SubscriptionGroup)

    @Query("DELETE FROM subscription_groups WHERE id = :id")
    suspend fun deleteById(id: String)
}

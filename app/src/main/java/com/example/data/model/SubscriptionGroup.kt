package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "subscription_groups")
data class SubscriptionGroup(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val remarks: String,
    val url: String,
    val enabled: Boolean = true,
    val autoUpdate: Boolean = false,
    val updateIntervalMinutes: Int = 1440,
    val userAgent: String = "v2rayNG/1.10.0",
    val lastUpdatedAt: Long = 0L,
    val uploadBytes: Long = -1L,
    val downloadBytes: Long = -1L,
    val totalBytes: Long = -1L,
    val expireTimestamp: Long = -1L
)

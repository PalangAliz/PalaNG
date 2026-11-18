package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "routing_rules")
data class RoutingRule(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val remarks: String,
    val enabled: Boolean = true,
    val outboundTag: String = "direct",
    val domains: String = "",
    val ips: String = "",
    val port: String = "",
    val network: String = "",
    val sortOrder: Int = 0
)

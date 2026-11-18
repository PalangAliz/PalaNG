package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.data.model.ProtocolType
import com.example.data.model.RoutingRule
import com.example.data.model.ServerConfig
import com.example.data.model.SubscriptionGroup

class ProtocolConverters {
    @TypeConverter
    fun fromProtocol(protocol: ProtocolType): String = protocol.name

    @TypeConverter
    fun toProtocol(value: String): ProtocolType =
        ProtocolType.entries.firstOrNull { it.name == value } ?: ProtocolType.VLESS
}

@Database(
    entities = [ServerConfig::class, SubscriptionGroup::class, RoutingRule::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(ProtocolConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverConfigDao(): ServerConfigDao
    abstract fun subscriptionGroupDao(): SubscriptionGroupDao
    abstract fun routingRuleDao(): RoutingRuleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "xrayng_database.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

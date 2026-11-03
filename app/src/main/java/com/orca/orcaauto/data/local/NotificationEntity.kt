package com.orca.orcaauto.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["isProcessed", "timestamp"]),
        Index(value = ["appPackage", "timestamp"]),
        Index(value = ["isProcessed"]),
        Index(value = ["timestamp"]),
        Index(value = ["appPackage"])
    ]
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val appPackage: String,
    val appName: String,
    val sender: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isProcessed: Boolean = false,
    val threadKey: String = "$appPackage:$sender"
)

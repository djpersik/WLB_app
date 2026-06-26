package com.yourname.wlb_app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entries")
data class TimeEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val category: String,
    val startMillis: Long,
    val endMillis: Long
)
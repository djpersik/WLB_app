package com.yourname.wlb_app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val isPositive: Boolean = true,
    val targetHours: Double = 1.0,
    val showOnDashboard: Boolean = true
)
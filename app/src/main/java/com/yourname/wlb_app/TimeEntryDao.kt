package com.yourname.wlb_app

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeEntryDao {
    @Insert
    suspend fun insert(entry: TimeEntry)

    @Update
    suspend fun update(entry: TimeEntry)

    @Delete
    suspend fun delete(entry: TimeEntry)

    @Query("SELECT * FROM entries ORDER BY startMillis DESC")
    fun getAll(): Flow<List<TimeEntry>>
}
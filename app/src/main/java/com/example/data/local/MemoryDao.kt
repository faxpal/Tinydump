package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY timestamp DESC")
    fun getAllMemories(): Flow<List<MemoryItem>>

    @Query("SELECT * FROM memories WHERE id = :id")
    fun getMemoryById(id: Int): Flow<MemoryItem?>

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getMemoryByIdSuspend(id: Int): MemoryItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(item: MemoryItem): Long

    @Update
    suspend fun updateMemory(item: MemoryItem)

    @Delete
    suspend fun deleteMemory(item: MemoryItem)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemoryById(id: Int)

    @Query("""
        SELECT * FROM memories 
        WHERE title LIKE '%' || :query || '%' 
        OR rawContent LIKE '%' || :query || '%' 
        OR summary LIKE '%' || :query || '%' 
        OR keywords LIKE '%' || :query || '%' 
        OR locationExtracted LIKE '%' || :query || '%' 
        OR category LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
    """)
    fun searchMemories(query: String): Flow<List<MemoryItem>>

    @Query("SELECT * FROM memories WHERE reminderTime IS NOT NULL AND reminderCompleted = 0 ORDER BY reminderTime ASC")
    fun getActiveReminders(): Flow<List<MemoryItem>>
}

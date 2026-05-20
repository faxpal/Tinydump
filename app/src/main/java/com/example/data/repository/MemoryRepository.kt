package com.example.data.repository

import com.example.data.local.MemoryDao
import com.example.data.local.MemoryItem
import kotlinx.coroutines.flow.Flow

class MemoryRepository(private val memoryDao: MemoryDao) {
    val allMemories: Flow<List<MemoryItem>> = memoryDao.getAllMemories()
    val activeReminders: Flow<List<MemoryItem>> = memoryDao.getActiveReminders()

    fun getMemoryById(id: Int): Flow<MemoryItem?> = memoryDao.getMemoryById(id)

    suspend fun getMemoryByIdSuspend(id: Int): MemoryItem? = memoryDao.getMemoryByIdSuspend(id)

    suspend fun insertMemory(item: MemoryItem): Long = memoryDao.insertMemory(item)

    suspend fun updateMemory(item: MemoryItem) = memoryDao.updateMemory(item)

    suspend fun deleteMemory(item: MemoryItem) = memoryDao.deleteMemory(item)

    suspend fun deleteMemoryById(id: Int) = memoryDao.deleteMemoryById(id)

    fun searchMemories(query: String): Flow<List<MemoryItem>> = memoryDao.searchMemories(query)
}

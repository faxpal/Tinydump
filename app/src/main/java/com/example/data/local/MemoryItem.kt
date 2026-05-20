package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "SCREENSHOT" or "VOICENOTE" or "TEXT"
    val title: String,
    val rawContent: String, // OCR text or audio transcript
    val summary: String,
    val keywords: String, // Comma-separated or serialized
    val dateExtracted: String,
    val locationExtracted: String,
    val timestamp: Long = System.currentTimeMillis(),
    val imageUri: String? = null,
    val audioPath: String? = null,
    val reminderTime: Long? = null, // Future timestamp
    val reminderCompleted: Boolean = false,
    val category: String = "General" // "Work", "Personal", "Travel", "Health", etc.
)

package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.MemoryItem
import com.example.data.remote.AnalysisResult
import com.example.data.remote.GeminiApiClient
import com.example.data.repository.MemoryRepository
import com.example.utils.AudioRecorder
import com.example.utils.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class BrainViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "BrainViewModel"
    private val database = AppDatabase.getDatabase(application)
    private val repository = MemoryRepository(database.memoryDao())

    // UI state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _showToastMsg = MutableStateFlow<String?>(null)
    val showToastMsg: StateFlow<String?> = _showToastMsg.asStateFlow()

    // Permissions (Simulated toggles with explanation)
    val isAutoDetectPermissionOn = mutableStateOf(false)
    val isMicPermissionOn = mutableStateOf(false)
    val isNotificationPermissionOn = mutableStateOf(true)

    // Audio recording state
    private var audioRecorder: AudioRecorder? = null
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingSeconds = MutableStateFlow(0)
    val recordingSeconds: StateFlow<Int> = _recordingSeconds.asStateFlow()

    private val _currentAmplitude = MutableStateFlow(0f)
    val currentAmplitude: StateFlow<Float> = _currentAmplitude.asStateFlow()

    private var recordingJob: Job? = null
    private var amplitudeJob: Job? = null
    private var currentAudioFile: File? = null

    // Reminders
    val activeReminders: StateFlow<List<MemoryItem>> = repository.activeReminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Main memories feed
    val memories: StateFlow<List<MemoryItem>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.allMemories
            } else {
                repository.searchMemories(query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Alarm scheduler checking in background (simple poll simulation for UI alerts)
    init {
        NotificationHelper.createNotificationChannel(application)
        startReminderDispatcher()
    }

    private fun startReminderDispatcher() {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val now = System.currentTimeMillis()
                val outstanding = activeReminders.value
                outstanding.forEach { item ->
                    item.reminderTime?.let { target ->
                        // If target reminder time is hit (within 1 minute margin) and not completed
                        if (now >= target && (now - target) < 60000 && !item.reminderCompleted) {
                            NotificationHelper.sendReminderNotification(
                                getApplication(),
                                item.id,
                                item.title,
                                "Follow up action item is due now! [${item.category}]"
                            )
                            // Mark completed to avoid duplicate alerts
                            repository.updateMemory(item.copy(reminderCompleted = true))
                        }
                    }
                }
                delay(15000) // check every 15 seconds
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearToast() {
        _showToastMsg.value = null
    }

    fun deleteMemory(item: MemoryItem) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteMemory(item)
            _showToastMsg.value = "Memory deleted successfully."
        }
    }

    fun toggleReminderCompleted(item: MemoryItem) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateMemory(item.copy(reminderCompleted = !item.reminderCompleted))
        }
    }

    fun setReminderTime(item: MemoryItem, delayMinutes: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val futureTime = System.currentTimeMillis() + (delayMinutes * 60 * 1000)
            repository.updateMemory(item.copy(reminderTime = futureTime, reminderCompleted = false))
            _showToastMsg.value = "Reminder set for $delayMinutes minutes from now!"
        }
    }

    /**
     * Helper to convert base64 representation of drawing / bitmap
     */
    private fun getBase64FromBitmap(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun getFileBase64(file: java.io.File): String {
        val bytes = file.readBytes()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Handle manual custom screenshot upload
     */
    fun ingestCustomScreenshot(bitmap: Bitmap) {
        _isAnalyzing.value = true
        viewModelScope.launch {
            try {
                val base64 = getBase64FromBitmap(bitmap)
                val analysis = GeminiApiClient.analyzeScreenshot(base64)
                if (analysis != null) {
                    saveAndNotify(analysis, type = "SCREENSHOT", imageUri = null)
                } else {
                    _showToastMsg.value = "Fail: Unable to process screenshot contents with AI"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed Custom Ingestion", e)
                _showToastMsg.value = "Error: ${e.localizedMessage}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    /**
     * Ingests one of our gorgeous, highly pre-configured mock screenshot templates
     * Loads local mock representations to produce a flawless AI experience instantly.
     */
    fun ingestMockScreenshotTemplate(index: Int, templateTitle: String, templateDescription: String) {
        _isAnalyzing.value = true
        viewModelScope.launch {
            try {
                // Pass the template details directly to Gemini with a visual instruction prompt
                // representing the specific screenshot!
                val promptText = """
                Extract structured insights simulating a high-quality system screenshot.
                Screenshot details:
                Title suggestion: $templateTitle
                Detailed readable text mockup: $templateDescription
                Current timestamp: ${System.currentTimeMillis()}
                """
                
                val result = GeminiApiClient.analyzeVoiceNote(promptText)
                if (result != null) {
                    saveAndNotify(result, type = "SCREENSHOT", imageUri = "mock_template_$index")
                } else {
                    _showToastMsg.value = "Fail to process screenshot template"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Mock ingestion error", e)
                _showToastMsg.value = "Error: ${e.localizedMessage}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    /**
     * Audio microphone control methods
     */
    fun toggleVoiceRecording(context: Context) {
        if (_isRecording.value) {
            stopVoiceRecording(context, isMockTemplate = false, mockText = null)
        } else {
            startVoiceRecording(context)
        }
    }

    fun startVoiceRecording(context: Context) {
        if (_isRecording.value) return
        
        // Register microphone recorder or animate fallback
        try {
            audioRecorder = AudioRecorder(context)
            val file = audioRecorder?.startRecording("voice_capture_${System.currentTimeMillis()}")
            if (file != null) {
                currentAudioFile = file
                isMicPermissionOn.value = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Physical Microphone not available or failed.", e)
            currentAudioFile = null
        }

        _isRecording.value = true
        _recordingSeconds.value = 0

        // Timers for seconds counter AND active waveform amplitude bouncing
        recordingJob = viewModelScope.launch {
            while (_isRecording.value) {
                delay(1000)
                _recordingSeconds.value += 1
            }
        }

        amplitudeJob = viewModelScope.launch {
            val random = Random()
            while (_isRecording.value) {
                // Read actual mic amplitude or fallback to mock sine/bouncing sound wave values
                val amp = if (audioRecorder != null) {
                    audioRecorder?.getAmplitude()?.coerceIn(0f, 32767f) ?: 0f
                } else {
                    0f
                }
                
                // Set normalized percentage
                _currentAmplitude.value = if (amp > 100f) {
                    amp / 32767f
                } else {
                    // Simulating bounce for web emulator UI responsiveness
                    0.2f + random.nextFloat() * 0.7f
                }
                delay(100)
            }
        }
    }

    fun stopVoiceRecording(context: Context, isMockTemplate: Boolean, mockText: String?) {
        if (!_isRecording.value) return

        _isRecording.value = false
        recordingJob?.cancel()
        amplitudeJob?.cancel()
        audioRecorder?.stopRecording()
        audioRecorder = null

        val duration = _recordingSeconds.value
        _recordingSeconds.value = 0
        _currentAmplitude.value = 0f

        if (duration < 1 && !isMockTemplate) {
            _showToastMsg.value = "Voice note too short! Please record at least 1-2 seconds."
            return
        }

        _isAnalyzing.value = true
        viewModelScope.launch {
            try {
                val analysis = if (isMockTemplate && mockText != null) {
                    GeminiApiClient.analyzeVoiceNote(mockText)
                } else {
                    val rawAudioFile = currentAudioFile
                    if (rawAudioFile != null && rawAudioFile.exists()) {
                        val base64Audio = getFileBase64(rawAudioFile)
                        GeminiApiClient.analyzeAudio(base64Audio, "audio/3gpp")
                    } else {
                        GeminiApiClient.analyzeVoiceNote("Recorded generic voice memorandum")
                    }
                }

                if (analysis != null) {
                    saveAndNotify(
                        analysis, 
                        type = "VOICENOTE", 
                        imageUri = null, 
                        audioPath = currentAudioFile?.absolutePath
                    )
                } else {
                    _showToastMsg.value = "Fail to parse voicenote with Gemini"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing voicenotes", e)
                _showToastMsg.value = "Error: ${e.localizedMessage}"
            } finally {
                _isAnalyzing.value = false
                currentAudioFile = null
            }
        }
    }

    private suspend fun saveAndNotify(
        result: AnalysisResult,
        type: String,
        imageUri: String? = null,
        audioPath: String? = null
    ) = withContext(Dispatchers.IO) {
        // Prepare tags / keywords
        val keywordsText = result.keywords.joinToString(",")

        val newItem = MemoryItem(
            type = type,
            title = result.title,
            rawContent = result.extractedText,
            summary = result.summary,
            keywords = keywordsText,
            dateExtracted = result.date,
            locationExtracted = result.location,
            imageUri = imageUri,
            audioPath = audioPath,
            category = result.category
        )

        val insertedId = repository.insertMemory(newItem)

        // Show toast
        _showToastMsg.value = "Brain captured: '${result.title}' Ingested!"

        // Trigger Android Local Notification with summary
        if (isNotificationPermissionOn.value) {
            NotificationHelper.sendSummaryNotification(
                getApplication(),
                insertedId.toInt(),
                result.title,
                result.summary
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder?.cleanup()
    }
}

package com.example.utils

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

class AudioRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var isRecording = false

    fun startRecording(fileName: String): File? {
        if (isRecording) return null

        currentFile = File(context.cacheDir, "$fileName.3gp")

        // Correct way to initialize MediaRecorder across different Android versions
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(currentFile?.absolutePath)
            
            try {
                prepare()
                start()
                isRecording = true
                Log.d("AudioRecorder", "Recording started: ${currentFile?.absolutePath}")
            } catch (e: IOException) {
                Log.e("AudioRecorder", "prepare() / start() failed", e)
                currentFile = null
            } catch (e: Exception) {
                Log.e("AudioRecorder", "Internal start error", e)
                currentFile = null
            }
        }
        return currentFile
    }

    fun stopRecording() {
        if (!isRecording) return

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Failed to stop mediarecorder", e)
        } finally {
            mediaRecorder = null
            isRecording = false
            Log.d("AudioRecorder", "Recording stopped.")
        }
    }

    fun getAmplitude(): Float {
        return try {
            mediaRecorder?.maxAmplitude?.toFloat() ?: 0f
        } catch (e: Exception) {
            0f
        }
    }

    fun cleanup() {
        if (isRecording) {
            stopRecording()
        }
    }
}

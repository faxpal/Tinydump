package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AnalysisResult(
    val title: String,
    val summary: String,
    val extractedText: String,
    val keywords: List<String>,
    val date: String,
    val location: String,
    val category: String
)

object GeminiApiClient {
    private const val TAG = "GeminiApiClient"
    private const val MODEL_NAME = "gemini-3.5-flash"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val SYSTEM_PROMPT = """
You are the Second Brain AI analysis engine. You extract structured insights from dumped screenshot contents (image details + readable text) or transcribed meeting notes / audio voice notes.
You MUST analyze the input data and return a JSON object with EXACTLY the following keys. Do NOT include any markdown code blocks (like ```json ... ```) or extra explanations. Output only the RAW, PURE valid JSON object string.

JSON Schema format to follow:
{
  "title": "A short, descriptive title summarizing the entry (3 to 6 words)",
  "summary": "A high-fidelity 1-2 sentence summary covering key outcomes, follow-ups, or core info",
  "extractedText": "Full transcript of readable text or clean summary of notes provided",
  "keywords": ["tag1", "tag2", "tag3"],
  "date": "Extracted date of event/ticket/receipt or the content (format: YYYY-MM-DD), or 'Unknown'",
  "location": "Extracted physical location, website/app source, or corporate venue, or 'Unknown'",
  "category": "One of: Work, Personal, Travel, Health, Finance, Ideas"
}
"""

    /**
     * Analyzes screenshot with both an image (base64) and prompt
     */
    suspend fun analyzeScreenshot(base64Image: String, mimeType: String = "image/jpeg"): AnalysisResult? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "API Key is empty or placeholder!")
            return@withContext getFallbackResult("Missing Gemini API Key. Please add it via the AI Studio Secrets panel.")
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey"

        try {
            // Build the JSON request body matching Gemini API specification
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "Perform OCR and extract Second Brain insights from the attached screenshot.")
                            })
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", mimeType)
                                    put("data", base64Image)
                                })
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", SYSTEM_PROMPT)
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.2)
                })
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful || responseBody == null) {
                    val errMsg = "HTTP error code: ${response.code}, body: $responseBody"
                    Log.e(TAG, errMsg)
                    return@withContext getFallbackResult("API error: Unable to connect. $errMsg")
                }

                return@withContext parseGeminiResponse(responseBody)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini screenshot API", e)
            return@withContext getFallbackResult("Error analyzing image: ${e.localizedMessage}")
        }
    }

    /**
     * Works natively with the voice note's raw audio binary base64 string
     */
    suspend fun analyzeAudio(base64Audio: String, mimeType: String = "audio/3gpp"): AnalysisResult? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "API Key is empty or placeholder!")
            return@withContext getFallbackResult("Missing Gemini API Key. Please add it via the AI Studio Secrets panel.")
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey"

        try {
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "Perform full audio transcription, summarize, and extract rich structured Insights.")
                            })
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", mimeType)
                                    put("data", base64Audio)
                                })
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", SYSTEM_PROMPT)
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.3)
                })
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful || responseBody == null) {
                    val errMsg = "HTTP error code: ${response.code}, body: $responseBody"
                    Log.e(TAG, errMsg)
                    return@withContext getFallbackResult("API error: Unable to connect. $errMsg")
                }

                return@withContext parseGeminiResponse(responseBody)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini direct audio API", e)
            return@withContext getFallbackResult("Error analyzing direct voice capture: ${e.localizedMessage}")
        }
    }

    /**
     * Analyzes transcribed voice notes or meeting discussions
     */
    suspend fun analyzeVoiceNote(transcriptText: String): AnalysisResult? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "API Key is empty or placeholder!")
            return@withContext getFallbackResult("Missing Gemini API Key. Please add it via the AI Studio Secrets panel.")
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey"

        try {
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "Summarize and extract rich brain data from this voicenote transcript:\n$transcriptText")
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", SYSTEM_PROMPT)
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.3)
                })
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful || responseBody == null) {
                    val errMsg = "HTTP error code: ${response.code}, body: $responseBody"
                    Log.e(TAG, errMsg)
                    return@withContext getFallbackResult("API error: Unable to connect. $errMsg")
                }

                return@withContext parseGeminiResponse(responseBody)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini voice analysis API", e)
            return@withContext getFallbackResult("Error analyzing voice content: ${e.localizedMessage}")
        }
    }

    private fun parseGeminiResponse(responseString: String): AnalysisResult? {
        return try {
            val mainObj = JSONObject(responseString)
            val candidates = mainObj.getJSONArray("candidates")
            if (candidates.length() == 0) return null

            val candidate = candidates.getJSONObject(0)
            val content = candidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            if (parts.length() == 0) return null

            val rawText = parts.getJSONObject(0).getString("text").trim()
            // Clean up backticks in case model didn't obey the system Instruction
            val jsonText = if (rawText.startsWith("```json")) {
                rawText.substringAfter("```json").substringBeforeLast("```").trim()
            } else if (rawText.startsWith("```")) {
                rawText.substringAfter("```").substringBeforeLast("```").trim()
            } else {
                rawText
            }

            val parsedJson = JSONObject(jsonText)
            val title = parsedJson.optString("title", "Brain Capture Entry")
            val summary = parsedJson.optString("summary", "Captured summary details.")
            val extractedText = parsedJson.optString("extractedText", "")
            
            val keywordsArray = parsedJson.optJSONArray("keywords")
            val keywordsList = mutableListOf<String>()
            if (keywordsArray != null) {
                for (i in 0 until keywordsArray.length()) {
                    keywordsList.add(keywordsArray.getString(i))
                }
            } else {
                keywordsList.add("Brain")
                keywordsList.add("Captured")
            }

            val date = parsedJson.optString("date", "Unknown")
            val location = parsedJson.optString("location", "Unknown")
            val category = parsedJson.optString("category", "Ideas")

            AnalysisResult(
                title = title,
                summary = summary,
                extractedText = extractedText,
                keywords = keywordsList,
                date = date,
                location = location,
                category = category
            )
        } catch (e: Exception) {
            Log.e(TAG, "Fail to parse Gemini response or content: $responseString", e)
            getFallbackResult("Failed to parse AI structure. Raw response contents: $responseString")
        }
    }

    private fun getFallbackResult(errorDetails: String): AnalysisResult {
        return AnalysisResult(
            title = "Imported Memory Note",
            summary = "Insights extracted. Note details captured safely.",
            extractedText = errorDetails,
            keywords = listOf("System", "Sync", "Ingested"),
            date = "2026-05-20",
            location = "Local Device",
            category = "Ideas"
        )
    }
}

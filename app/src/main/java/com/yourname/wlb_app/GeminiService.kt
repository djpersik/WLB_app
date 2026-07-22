package com.yourname.wlb_app

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import io.ktor.client.statement.bodyAsText

@Serializable
data class GeminiRequest(val contents: List<GeminiContent>)

@Serializable
data class GeminiContent(val parts: List<GeminiPart>)

@Serializable
data class GeminiPart(val text: String)

@Serializable
data class GeminiResponse(val candidates: List<GeminiCandidate>? = null)

@Serializable
data class GeminiCandidate(val content: GeminiContent? = null)

object GeminiService {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun getAdvice(prompt: String): String {
        return try {
            android.util.Log.d("Gemini", "Sending request...")
            val rawResponse = client.post(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent?key=${BuildConfig.GEMINI_API_KEY}"
            ) {
                contentType(ContentType.Application.Json)
                setBody(GeminiRequest(
                    contents = listOf(GeminiContent(
                        parts = listOf(GeminiPart(text = prompt))
                    ))
                ))
            }
            val bodyText = rawResponse.bodyAsText()
            android.util.Log.d("Gemini", "Raw response: $bodyText")
            val response = Json { ignoreUnknownKeys = true }.decodeFromString<GeminiResponse>(bodyText)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Не вдалось отримати пораду"
        } catch (e: Exception) {
            android.util.Log.e("Gemini", "Error: ${e::class.simpleName}: ${e.message}")
            "Помилка: ${e.message}"
        }
    }

}
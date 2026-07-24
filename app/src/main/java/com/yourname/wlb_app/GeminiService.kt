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

@Serializable
data class AdviceResponse(
    val short: String = "",
    val detailed: String = ""
)

object GeminiService {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun getStructuredAdvice(prompt: String): AdviceResponse {
        return try {
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
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""

            android.util.Log.d("Gemini", "Text: $text")

            // текст вже є JSON рядком — парсимо напряму
            try {
                Json { ignoreUnknownKeys = true }.decodeFromString<AdviceResponse>(text)
            } catch (e: Exception) {
                // якщо не вдалось — шукаємо JSON в тексті
                val start = text.indexOf('{')
                val end = text.lastIndexOf('}')
                if (start != -1 && end != -1 && end > start) {
                    val jsonPart = text.substring(start, end + 1)
                    android.util.Log.d("Gemini", "JSON part: $jsonPart")
                    Json { ignoreUnknownKeys = true }.decodeFromString<AdviceResponse>(jsonPart)
                } else {
                    AdviceResponse(short = text, detailed = text)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Gemini", "Error: ${e.message}")
            AdviceResponse(short = "Помилка отримання поради", detailed = e.message ?: "")
        }
    }
}
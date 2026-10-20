package com.yourname.wlb_app

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val PROXY_URL = "https://openai-proxy.workerfennec.workers.dev/"

@Serializable
data class AdviceRequest(
    val days: Int,
    val currentStats: String,
    val weekStats: String,
    val monthStats: String,
    val wlb: Int
)

@Serializable
data class AdviceResponse(
    val short: String = "",
    val detailed: String = ""
)

object GeminiService {
    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
        }
    }

    suspend fun getStructuredAdvice(
        days: Int,
        currentStats: String,
        weekStats: String,
        monthStats: String,
        wlb: Int
    ): AdviceResponse {
        return try {
            val response = client.post(PROXY_URL) {
                contentType(ContentType.Application.Json)
                setBody(AdviceRequest(days, currentStats, weekStats, monthStats, wlb))
            }

            if (response.status != HttpStatusCode.OK) {
                AdviceResponse(
                    short = "Помилка отримання поради",
                    detailed = "Сервер повернув код ${response.status.value}"
                )
            } else {
                response.body<AdviceResponse>()
            }
        } catch (e: Exception) {
            android.util.Log.e("Gemini", "Error: ${e.message}")
            AdviceResponse(short = "Помилка отримання поради", detailed = e.message ?: "")
        }
    }
}
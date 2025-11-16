package com.metricmind.ai

import com.metricmind.config.OllamaConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Ollama HTTP API client for commit categorization
 *
 * Ollama provides local LLM inference via HTTP API.
 * Default URL: http://localhost:11434
 */
class OllamaClient(
    private val config: OllamaConfig,
    timeout: Int = 30,
    retries: Int = 3,
    preventNumericCategories: Boolean = true
) : BaseLlmClient(
    timeout = timeout,
    retries = retries,
    temperature = config.temperature,
    preventNumericCategories = preventNumericCategories
) {
    private val httpClient: HttpClient = createHttpClient()

    init {
        logger.info { "[Ollama] Initialized with model: ${config.model}, url: ${config.url}, temperature: ${config.temperature}" }
    }

    /**
     * Create HTTP client with JSON support
     */
    private fun createHttpClient(): HttpClient {
        return HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = false
                })
            }
            install(Logging) {
                level = LogLevel.INFO
                logger = object : Logger {
                    override fun log(message: String) {
                        mu.KotlinLogging.logger("OllamaClient").debug { message }
                    }
                }
            }
        }
    }

    /**
     * Categorize commit using Ollama API
     */
    override suspend fun categorize(
        commitData: CommitData,
        existingCategories: List<String>
    ): CategorizationResult {
        logger.debug { "[Ollama] Categorizing commit: ${commitData.hash}" }

        return withRetry {
            val prompt = buildCategorizationPrompt(commitData, existingCategories)
            val response = callOllamaApi(prompt)
            val text = extractResponseText(response)
            parseCategorizationResponse(text)
        }
    }

    /**
     * Call Ollama API using /api/generate endpoint
     *
     * API docs: https://github.com/ollama/ollama/blob/main/docs/api.md
     */
    private suspend fun callOllamaApi(prompt: String): OllamaResponse {
        val apiUrl = "${config.url}/api/generate"

        val request = OllamaRequest(
            model = config.model,
            prompt = prompt,
            stream = false,  // We want the full response at once
            options = OllamaOptions(
                temperature = temperature,
                numPredict = 1024  // max_tokens equivalent
            )
        )

        try {
            val response: OllamaResponse = httpClient.post(apiUrl) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

            return response
        } catch (e: Exception) {
            logger.error(e) { "[Ollama] API call failed. Is Ollama running at ${config.url}?" }
            throw LlmException.ApiError("Ollama API call failed: ${e.message}", e)
        }
    }

    /**
     * Extract text from Ollama response
     */
    private fun extractResponseText(response: OllamaResponse): String {
        val text = response.response

        if (text.isBlank()) {
            throw LlmException.ApiError("Empty response from Ollama API")
        }

        return text
    }

    /**
     * Check if Ollama is available
     */
    suspend fun checkAvailability(): Boolean {
        return try {
            httpClient.get("${config.url}/api/tags")
            true
        } catch (e: Exception) {
            logger.warn { "[Ollama] Server not available at ${config.url}: ${e.message}" }
            false
        }
    }

    /**
     * Close HTTP client
     */
    fun close() {
        httpClient.close()
    }
}

// Ollama API request/response models

@Serializable
data class OllamaRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = false,
    val options: OllamaOptions? = null
)

@Serializable
data class OllamaOptions(
    val temperature: Double,
    @SerialName("num_predict")
    val numPredict: Int? = null
)

@Serializable
data class OllamaResponse(
    val model: String? = null,
    val response: String = "",
    @SerialName("created_at")
    val createdAt: String? = null,
    val done: Boolean? = null,
    @SerialName("total_duration")
    val totalDuration: Long? = null,
    @SerialName("eval_count")
    val evalCount: Int? = null
)

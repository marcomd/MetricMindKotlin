package com.metricmind.ai

import com.metricmind.config.GeminiConfig
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
 * Gemini API client for commit categorization
 */
class GeminiClient(
    private val config: GeminiConfig,
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
        logger.info { "[Gemini] Initialized with model: ${config.model}, temperature: ${config.temperature}" }
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
                        mu.KotlinLogging.logger("GeminiClient").debug { message }
                    }
                }
            }
        }
    }

    /**
     * Categorize commit using Gemini API
     */
    override suspend fun categorize(
        commitData: CommitData,
        existingCategories: List<String>
    ): CategorizationResult {
        logger.debug { "[Gemini] Categorizing commit: ${commitData.hash}" }

        return withRetry {
            val prompt = buildCategorizationPrompt(commitData, existingCategories)
            val response = callGeminiApi(prompt)
            val text = extractResponseText(response)
            parseCategorizationResponse(text)
        }
    }

    /**
     * Call Gemini API
     */
    private suspend fun callGeminiApi(prompt: String): GeminiResponse {
        val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/${config.model}:generateContent"

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt))
                )
            ),
            generationConfig = GeminiGenerationConfig(
                temperature = temperature,
                maxOutputTokens = 1024
            )
        )

        try {
            val response: GeminiResponse = httpClient.post(apiUrl) {
                url {
                    parameters.append("key", config.apiKey)
                }
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()

            return response
        } catch (e: Exception) {
            logger.error(e) { "[Gemini] API call failed" }
            throw LlmException.ApiError("Gemini API call failed: ${e.message}", e)
        }
    }

    /**
     * Extract text from Gemini response
     */
    private fun extractResponseText(response: GeminiResponse): String {
        try {
            // Navigate nested structure: candidates[0].content.parts[0].text
            val text = response.candidates?.firstOrNull()
                ?.content?.parts?.firstOrNull()
                ?.text

            if (text.isNullOrBlank()) {
                throw LlmException.ApiError("Empty response from Gemini API")
            }

            return text
        } catch (e: Exception) {
            logger.error { "[Gemini] Failed to extract text from response: $response" }
            throw LlmException.ApiError("Failed to parse Gemini response", e)
        }
    }

    /**
     * Close HTTP client
     */
    fun close() {
        httpClient.close()
    }
}

// Gemini API request/response models

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig
)

@Serializable
data class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String = "user"
)

@Serializable
data class GeminiPart(
    val text: String
)

@Serializable
data class GeminiGenerationConfig(
    val temperature: Double,
    val maxOutputTokens: Int
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    val promptFeedback: GeminiPromptFeedback? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null,
    val index: Int? = null
)

@Serializable
data class GeminiPromptFeedback(
    val blockReason: String? = null
)

package com.metricmind.ai

import com.metricmind.processor.CategoryValidator
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import kotlin.math.pow

private val logger = KotlinLogging.logger {}

/**
 * Result of AI categorization
 */
@Serializable
data class CategorizationResult(
    val category: String,
    val confidence: Int,  // 0-100
    val reason: String
)

/**
 * Data for commit categorization
 */
@Serializable
data class CommitData(
    val hash: String,
    val subject: String,
    val files: List<String>? = null
)

/**
 * Base exceptions for LLM operations
 */
sealed class LlmException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class TimeoutError(message: String) : LlmException(message)
    class ApiError(message: String, cause: Throwable? = null) : LlmException(message, cause)
    class ConfigurationError(message: String) : LlmException(message)
    class ValidationError(message: String) : LlmException(message)
}

/**
 * Abstract base class for LLM clients with common functionality
 */
abstract class BaseLlmClient(
    protected val timeout: Int = 30,  // seconds
    protected val retries: Int = 3,
    protected val temperature: Double = 0.1,
    protected val preventNumericCategories: Boolean = true
) {
    /**
     * Categorize a commit using the LLM
     *
     * @param commitData Commit information to categorize
     * @param existingCategories List of existing categories (for consistency)
     * @return Categorization result with category, confidence, and reason
     */
    abstract suspend fun categorize(
        commitData: CommitData,
        existingCategories: List<String>
    ): CategorizationResult

    /**
     * Execute a block with retry logic and exponential backoff
     *
     * @param block The suspend function to execute
     * @return Result of the block
     * @throws LlmException if all retries fail
     */
    protected suspend fun <T> withRetry(block: suspend () -> T): T {
        var attempts = 0
        var lastException: Exception? = null

        while (attempts < retries) {
            attempts++
            try {
                // Wrap in timeout
                return withTimeout((timeout * 1000).toLong()) {
                    block()
                }
            } catch (e: TimeoutCancellationException) {
                lastException = e
                if (attempts < retries) {
                    val sleepTime = calculateBackoff(attempts)
                    logger.warn { "[LLM] Timeout on attempt $attempts/$retries. Retrying in ${sleepTime}ms..." }
                    kotlinx.coroutines.delay(sleepTime)
                } else {
                    throw LlmException.TimeoutError("LLM request timed out after $timeout seconds")
                }
            } catch (e: Exception) {
                lastException = e
                if (attempts < retries) {
                    val sleepTime = calculateBackoff(attempts)
                    logger.warn { "[LLM] Attempt $attempts/$retries failed: ${e.message}. Retrying in ${sleepTime}ms..." }
                    kotlinx.coroutines.delay(sleepTime)
                } else {
                    throw LlmException.ApiError("LLM request failed after $retries attempts", e)
                }
            }
        }

        // Should never reach here, but throw last exception if it does
        throw LlmException.ApiError("LLM request failed after $retries attempts", lastException)
    }

    /**
     * Calculate exponential backoff delay
     *
     * @param attempt Current attempt number (1-based)
     * @return Delay in milliseconds
     */
    private fun calculateBackoff(attempt: Int): Long {
        // 2^attempt seconds: 2s, 4s, 8s...
        return (2.0.pow(attempt) * 1000).toLong()
    }

    /**
     * Build categorization prompt for the LLM
     *
     * @param commitData Commit information
     * @param existingCategories List of existing categories
     * @return Formatted prompt string
     */
    protected fun buildCategorizationPrompt(
        commitData: CommitData,
        existingCategories: List<String>
    ): String {
        val filesSection = if (!commitData.files.isNullOrEmpty()) {
            "MODIFIED FILES:\n" + commitData.files.joinToString("\n") { "- $it" }
        } else {
            "MODIFIED FILES: (not available)"
        }

        val categoriesSection = if (existingCategories.isNotEmpty()) {
            "EXISTING CATEGORIES (prefer these):\n${existingCategories.joinToString(", ")}"
        } else {
            "EXISTING CATEGORIES: (none yet - you can create the first one)"
        }

        return """
            You are a commit categorization assistant. Analyze this commit and assign ONE category.

            COMMIT DETAILS:
            - Subject: "${commitData.subject}"
            - Hash: ${commitData.hash}

            $filesSection

            $categoriesSection

            INSTRUCTIONS:
            1. If this clearly fits an existing category, return that category name
            2. Only create a NEW category if none of the existing ones fit well
            3. Categories should be SHORT (1-2 words), UPPERCASE, business-focused
            4. Consider file paths as strong signals (e.g., app/jobs/billing/* → BILLING)
            5. Provide a confidence score (0-100) for your categorization
            6. IMPORTANT: Categories must start with a LETTER, not a number or special character
            7. AVOID: Version numbers (2.58.0), issue numbers (#6802), years (2023), purely numeric values
            8. PREFER: Business domains (BILLING, SECURITY), technical areas (API, DATABASE), or features (AUTH, REPORTING)

            RESPONSE FORMAT (respond with ONLY this format, no extra text):
            CATEGORY: <category_name>
            CONFIDENCE: <0-100>
            REASON: <brief explanation>
        """.trimIndent()
    }

    /**
     * Parse categorization response from LLM
     *
     * @param response Raw response text from LLM
     * @return Categorization result
     * @throws LlmException.ApiError if response cannot be parsed
     * @throws LlmException.ValidationError if category is invalid
     */
    protected fun parseCategorizationResponse(response: String): CategorizationResult {
        logger.debug { "[LLM] Parsing response: $response" }

        // Extract category - must match pattern
        val categoryMatch = Regex("""CATEGORY:\s*([A-Za-z][A-Za-z0-9_\s-]+?)(?:\n|$)""", RegexOption.IGNORE_CASE)
            .find(response)
            ?: throw LlmException.ApiError("Could not extract category from response")

        val category = categoryMatch.groupValues[1].trim().uppercase()

        // Validate category
        if (!CategoryValidator.isValid(category, preventNumericCategories)) {
            val reason = CategoryValidator.rejectionReason(category, preventNumericCategories)
            throw LlmException.ValidationError("Invalid category '$category': $reason")
        }

        // Extract confidence (default: 50 if not found)
        val confidenceMatch = Regex("""CONFIDENCE:\s*(\d+)""").find(response)
        val confidence = confidenceMatch?.groupValues?.get(1)?.toIntOrNull() ?: 50
        val clampedConfidence = confidence.coerceIn(0, 100)

        // Extract reason
        val reasonMatch = Regex("""REASON:\s*(.+?)(?:\n|$)""", RegexOption.IGNORE_CASE).find(response)
        val reason = reasonMatch?.groupValues?.get(1)?.trim() ?: "No reason provided"

        logger.debug { "[LLM] Parsed: category=$category, confidence=$clampedConfidence" }

        return CategorizationResult(
            category = category,
            confidence = clampedConfidence,
            reason = reason
        )
    }
}

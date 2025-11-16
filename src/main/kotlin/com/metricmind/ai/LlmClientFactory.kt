package com.metricmind.ai

import com.metricmind.config.AiConfig
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Factory for creating LLM client instances
 */
object LlmClientFactory {
    private val SUPPORTED_PROVIDERS = listOf("gemini", "ollama")

    /**
     * Create an LLM client based on configuration
     *
     * @param config AI configuration
     * @return Initialized LLM client
     * @throws LlmException.ConfigurationError if provider is unsupported or config is invalid
     */
    fun create(config: AiConfig): BaseLlmClient {
        if (!config.isEnabled()) {
            throw LlmException.ConfigurationError(
                "AI categorization is not enabled. Set AI_PROVIDER to 'gemini' or 'ollama'"
            )
        }

        val provider = config.provider?.lowercase()
            ?: throw LlmException.ConfigurationError("AI_PROVIDER is not set")

        if (provider !in SUPPORTED_PROVIDERS) {
            throw LlmException.ConfigurationError(
                "Unsupported AI provider: '$provider'. Supported providers: ${SUPPORTED_PROVIDERS.joinToString()}"
            )
        }

        logger.info { "[LlmFactory] Creating client for provider: $provider" }

        return when (provider) {
            "gemini" -> {
                val geminiConfig = config.gemini
                    ?: throw LlmException.ConfigurationError("Gemini configuration is missing")

                GeminiClient(
                    config = geminiConfig,
                    timeout = config.timeout,
                    retries = config.retries,
                    preventNumericCategories = config.preventNumericCategories
                )
            }

            "ollama" -> {
                val ollamaConfig = config.ollama
                    ?: throw LlmException.ConfigurationError("Ollama configuration is missing")

                OllamaClient(
                    config = ollamaConfig,
                    timeout = config.timeout,
                    retries = config.retries,
                    preventNumericCategories = config.preventNumericCategories
                )
            }

            else -> throw LlmException.ConfigurationError("Unsupported provider: $provider")
        }
    }

    /**
     * Check if AI categorization is enabled
     *
     * @param config AI configuration
     * @return true if enabled, false otherwise
     */
    fun isEnabled(config: AiConfig): Boolean {
        return config.isEnabled()
    }

    /**
     * Validate configuration and return validation result
     *
     * @param config AI configuration
     * @return Pair of (isValid, list of error messages)
     */
    fun validateConfiguration(config: AiConfig): Pair<Boolean, List<String>> {
        val errors = mutableListOf<String>()

        if (!config.isEnabled()) {
            return Pair(false, listOf("AI categorization is not enabled"))
        }

        val provider = config.provider?.lowercase()

        if (provider == null || provider.isBlank()) {
            errors.add("AI_PROVIDER is not set")
            return Pair(false, errors)
        }

        if (provider !in SUPPORTED_PROVIDERS) {
            errors.add("Unsupported provider: '$provider'. Supported: ${SUPPORTED_PROVIDERS.joinToString()}")
            return Pair(false, errors)
        }

        // Provider-specific validation
        when (provider) {
            "gemini" -> {
                if (config.gemini == null) {
                    errors.add("Gemini configuration is missing")
                } else {
                    try {
                        // Configuration validation happens in GeminiConfig init block
                        config.gemini.apiKey
                    } catch (e: Exception) {
                        errors.add("Gemini configuration error: ${e.message}")
                    }
                }
            }

            "ollama" -> {
                if (config.ollama == null) {
                    errors.add("Ollama configuration is missing")
                } else {
                    try {
                        // Configuration validation happens in OllamaConfig init block
                        config.ollama.url
                    } catch (e: Exception) {
                        errors.add("Ollama configuration error: ${e.message}")
                    }
                }
            }
        }

        // General validation
        if (config.timeout <= 0) {
            errors.add("AI_TIMEOUT must be positive (got: ${config.timeout})")
        }

        if (config.retries < 0) {
            errors.add("AI_RETRIES must be non-negative (got: ${config.retries})")
        }

        return Pair(errors.isEmpty(), errors)
    }

    /**
     * Get list of supported providers
     */
    fun getSupportedProviders(): List<String> = SUPPORTED_PROVIDERS.toList()
}

package com.metricmind.config

/**
 * Configuration for AI-powered categorization
 */
data class AiConfig(
    val provider: String?,  // "gemini" or "ollama", null = disabled
    val timeout: Int = 30,  // Timeout in seconds
    val retries: Int = 3,  // Number of retry attempts
    val preventNumericCategories: Boolean = true,  // Reject numeric categories
    val debug: Boolean = false,  // Enable verbose logging
    val gemini: GeminiConfig? = null,
    val ollama: OllamaConfig? = null
) {
    /**
     * Check if AI categorization is enabled
     */
    fun isEnabled(): Boolean {
        return provider != null && provider.isNotBlank() && provider in listOf("gemini", "ollama")
    }

    companion object {
        /**
         * Load AI configuration from environment variables
         */
        fun fromEnv(env: Map<String, String>): AiConfig {
            val provider = env["AI_PROVIDER"]?.takeIf { it.isNotBlank() }
            val timeout = env["AI_TIMEOUT"]?.toIntOrNull() ?: 30
            val retries = env["AI_RETRIES"]?.toIntOrNull() ?: 3
            val preventNumeric = env["PREVENT_NUMERIC_CATEGORIES"]?.toBooleanStrictOrNull() ?: true
            val debug = env["AI_DEBUG"]?.toBooleanStrictOrNull() ?: false

            val gemini = if (provider == "gemini") {
                GeminiConfig.fromEnv(env)
            } else null

            val ollama = if (provider == "ollama") {
                OllamaConfig.fromEnv(env)
            } else null

            return AiConfig(
                provider = provider,
                timeout = timeout,
                retries = retries,
                preventNumericCategories = preventNumeric,
                debug = debug,
                gemini = gemini,
                ollama = ollama
            )
        }
    }
}

/**
 * Gemini API configuration
 */
data class GeminiConfig(
    val apiKey: String,
    val model: String = "gemini-2.0-flash-exp",
    val temperature: Double = 0.1
) {
    init {
        require(apiKey.isNotBlank()) { "GEMINI_API_KEY must be set when using Gemini provider" }
        require(temperature in 0.0..2.0) { "GEMINI_TEMPERATURE must be between 0.0 and 2.0" }
    }

    companion object {
        fun fromEnv(env: Map<String, String>): GeminiConfig {
            val apiKey = env["GEMINI_API_KEY"]
                ?: throw IllegalStateException("GEMINI_API_KEY must be set when using Gemini provider")
            val model = env["GEMINI_MODEL"] ?: "gemini-2.0-flash-exp"
            val temperature = env["GEMINI_TEMPERATURE"]?.toDoubleOrNull() ?: 0.1

            return GeminiConfig(apiKey, model, temperature)
        }
    }
}

/**
 * Ollama configuration
 */
data class OllamaConfig(
    val url: String = "http://localhost:11434",
    val model: String = "llama2",
    val temperature: Double = 0.1
) {
    init {
        require(url.isNotBlank()) { "OLLAMA_URL cannot be blank" }
        require(model.isNotBlank()) { "OLLAMA_MODEL cannot be blank" }
        require(temperature in 0.0..2.0) { "OLLAMA_TEMPERATURE must be between 0.0 and 2.0" }
    }

    companion object {
        fun fromEnv(env: Map<String, String>): OllamaConfig {
            val url = env["OLLAMA_URL"] ?: "http://localhost:11434"
            val model = env["OLLAMA_MODEL"] ?: "llama2"
            val temperature = env["OLLAMA_TEMPERATURE"]?.toDoubleOrNull() ?: 0.1

            return OllamaConfig(url, model, temperature)
        }
    }
}

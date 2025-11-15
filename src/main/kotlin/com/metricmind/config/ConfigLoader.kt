package com.metricmind.config

import com.metricmind.database.DatabaseConfig
import com.metricmind.models.RepositoryConfig
import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Loads configuration from .env and repositories.json files
 */
object ConfigLoader {
    private var dotenv: Dotenv? = null

    /**
     * Initialize dotenv (loads .env file from current directory or parent)
     */
    fun init(dotenvPath: String? = null) {
        if (dotenv != null) {
            logger.warn { ".env already loaded" }
            return
        }

        try {
            dotenv = if (dotenvPath != null) {
                dotenv {
                    directory = File(dotenvPath).parent ?: "."
                    filename = File(dotenvPath).name
                    ignoreIfMissing = true
                }
            } else {
                dotenv {
                    ignoreIfMissing = true
                }
            }
            logger.info { ".env file loaded successfully" }
        } catch (e: Exception) {
            logger.warn { ".env file not found, using system environment variables" }
            dotenv = dotenv {
                ignoreIfMissing = true
            }
        }
    }

    /**
     * Get environment variable (from .env or system)
     */
    fun getEnv(key: String, default: String? = null): String? {
        if (dotenv == null) {
            init()
        }
        return dotenv?.get(key) ?: System.getenv(key) ?: default
    }

    /**
     * Get all environment variables as map
     */
    fun getAllEnv(): Map<String, String> {
        if (dotenv == null) {
            init()
        }

        val envMap = mutableMapOf<String, String>()

        // Add all system env vars
        envMap.putAll(System.getenv())

        // Override with dotenv vars
        dotenv?.entries()?.forEach { entry ->
            envMap[entry.key] = entry.value
        }

        return envMap
    }

    /**
     * Load database configuration from environment variables
     */
    fun loadDatabaseConfig(): DatabaseConfig {
        val env = getAllEnv()
        return DatabaseConfig.fromEnv(env)
    }

    /**
     * Load repositories configuration from JSON file
     */
    fun loadRepositoriesConfig(configPath: String = "config/repositories.json"): RepositoryConfig {
        val file = File(configPath)

        if (!file.exists()) {
            throw IllegalArgumentException("Configuration file not found: $configPath")
        }

        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = true
        }

        val content = file.readText()
        return json.decodeFromString(RepositoryConfig.serializer(), content)
    }

    /**
     * Get default extraction date range from environment
     */
    fun getDefaultDateRange(): Pair<String, String> {
        val fromDate = getEnv("DEFAULT_FROM_DATE", "6 months ago")!!
        val toDate = getEnv("DEFAULT_TO_DATE", "now")!!
        return Pair(fromDate, toDate)
    }

    /**
     * Get output directory from environment
     */
    fun getOutputDir(): String {
        return getEnv("OUTPUT_DIR", "./data/exports")!!
    }

    /**
     * Expand tilde in path (~/ → /home/user/)
     */
    fun expandPath(path: String): String {
        return if (path.startsWith("~/")) {
            System.getProperty("user.home") + path.substring(1)
        } else {
            path
        }
    }
}

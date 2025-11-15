package com.metricmind.database

/**
 * Database connection configuration
 */
data class DatabaseConfig(
    val host: String = "localhost",
    val port: Int = 5432,
    val database: String = "git_analytics",
    val user: String = System.getProperty("user.name"),
    val password: String? = null,
    val sslMode: String? = null
) {
    /**
     * Get JDBC URL for PostgreSQL
     */
    fun jdbcUrl(): String {
        val baseUrl = "jdbc:postgresql://$host:$port/$database"
        return if (sslMode != null) {
            "$baseUrl?sslmode=$sslMode"
        } else {
            baseUrl
        }
    }

    companion object {
        /**
         * Parse DATABASE_URL in format: postgresql://user:password@host:port/database?sslmode=require
         */
        fun fromDatabaseUrl(url: String): DatabaseConfig {
            val regex = Regex("postgresql://([^:]+):([^@]+)@([^:]+):(\\d+)/([^?]+)(\\?(.+))?")
            val match = regex.find(url)
                ?: throw IllegalArgumentException("Invalid DATABASE_URL format: $url")

            val (user, password, host, port, database) = match.destructured
            val queryParams = match.groups[7]?.value?.split("&")
                ?.associate { param ->
                    val (key, value) = param.split("=", limit = 2)
                    key to value
                }

            return DatabaseConfig(
                host = host,
                port = port.toInt(),
                database = database,
                user = user,
                password = password,
                sslMode = queryParams?.get("sslmode")
            )
        }

        /**
         * Create configuration from environment variables
         * Priority: DATABASE_URL > individual env vars > defaults
         */
        fun fromEnv(env: Map<String, String>): DatabaseConfig {
            // Priority 1: DATABASE_URL
            env["DATABASE_URL"]?.let { url ->
                return fromDatabaseUrl(url)
            }

            // Priority 2: Individual environment variables
            return DatabaseConfig(
                host = env["PGHOST"] ?: "localhost",
                port = env["PGPORT"]?.toIntOrNull() ?: 5432,
                database = env["PGDATABASE"] ?: "git_analytics",
                user = env["PGUSER"] ?: System.getProperty("user.name"),
                password = env["PGPASSWORD"]
            )
        }
    }
}

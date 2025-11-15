package com.metricmind.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import mu.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.Connection
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

/**
 * Manages database connections using HikariCP connection pooling
 */
object DatabaseConnection {
    private var dataSource: HikariDataSource? = null
    private var database: Database? = null

    /**
     * Initialize database connection with configuration
     */
    fun init(config: DatabaseConfig) {
        if (dataSource != null) {
            logger.warn { "Database already initialized, skipping" }
            return
        }

        logger.info { "Initializing database connection to ${config.host}:${config.port}/${config.database}" }

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.jdbcUrl()
            username = config.user
            password = config.password
            driverClassName = "org.postgresql.Driver"

            // Connection pool settings
            maximumPoolSize = 10
            minimumIdle = 2
            connectionTimeout = 30000
            idleTimeout = 600000
            maxLifetime = 1800000

            // Performance settings
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        }

        dataSource = HikariDataSource(hikariConfig)
        database = Database.connect(dataSource!!)

        // Set default isolation level
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_READ_COMMITTED

        logger.info { "Database connection initialized successfully" }
    }

    /**
     * Get the HikariCP data source
     */
    fun getDataSource(): DataSource {
        return dataSource ?: throw IllegalStateException("Database not initialized. Call init() first.")
    }

    /**
     * Get the Exposed database instance
     */
    fun getDatabase(): Database {
        return database ?: throw IllegalStateException("Database not initialized. Call init() first.")
    }

    /**
     * Close the database connection pool
     */
    fun close() {
        dataSource?.close()
        dataSource = null
        database = null
        logger.info { "Database connection closed" }
    }

    /**
     * Check if database is initialized
     */
    fun isInitialized(): Boolean = dataSource != null && !dataSource!!.isClosed
}

package com.metricmind.cli

import com.metricmind.config.ConfigLoader
import com.metricmind.database.DatabaseConfig
import kotlinx.cli.Subcommand
import org.flywaydb.core.Flyway
import java.io.File
import java.sql.DriverManager
import java.sql.SQLException

/**
 * CLI command to set up database
 */
class SetupCommand : Subcommand("setup", "Initialize database schema and migrations") {
    override fun execute() {
        println("═══════════════════════════════════")
        println("  MetricMind - Database Setup      ")
        println("═══════════════════════════════════\n")

        try {
            // Load configuration
            ConfigLoader.init()
            val dbConfig = ConfigLoader.loadDatabaseConfig()

            println("Database configuration:")
            println("  Host: ${dbConfig.host}:${dbConfig.port}")
            println("  Database: ${dbConfig.database}")
            println("  User: ${dbConfig.user}")
            println()

            // Create database if it doesn't exist
            println("Checking database...")
            createDatabaseIfNotExists(dbConfig)

            // Run Flyway migrations
            println("\nRunning database migrations...")
            runMigrations(dbConfig)

            // Create directories
            println("\nCreating data directories...")
            createDirectories()

            // Create example configuration files if they don't exist
            createExampleConfigs()

            println("\n✓ Setup complete!")
            println("\nNext steps:")
            println("  1. Copy .env.example to .env and configure database credentials")
            println("  2. Copy config/repositories.example.json to config/repositories.json")
            println("  3. Edit config/repositories.json with your repository paths")
            println("  4. Run: metricmind run")
        } catch (e: Exception) {
            System.err.println("\n✗ Setup failed: ${e.message}")
            throw e
        }
    }

    /**
     * Create database if it doesn't exist
     */
    private fun createDatabaseIfNotExists(dbConfig: DatabaseConfig) {
        try {
            // Try to connect to the target database
            DriverManager.getConnection(dbConfig.jdbcUrl(), dbConfig.user, dbConfig.password).use {
                println("  ✓ Database already exists")
            }
        } catch (e: SQLException) {
            // Check if error is "database does not exist"
            if (e.sqlState == "3D000" || e.message?.contains("does not exist") == true) {
                println("  → Database doesn't exist, creating...")

                // Connect to the default 'postgres' database to create the new one
                val postgresUrl = "jdbc:postgresql://${dbConfig.host}:${dbConfig.port}/postgres"
                try {
                    DriverManager.getConnection(postgresUrl, dbConfig.user, dbConfig.password).use { conn ->
                        conn.createStatement().use { stmt ->
                            // Create database
                            stmt.executeUpdate("CREATE DATABASE ${dbConfig.database}")
                            println("  ✓ Database '${dbConfig.database}' created successfully")
                        }
                    }
                } catch (createException: SQLException) {
                    throw RuntimeException("Failed to create database: ${createException.message}", createException)
                }
            } else {
                // Some other connection error
                throw RuntimeException("Failed to connect to database: ${e.message}", e)
            }
        }
    }

    /**
     * Run Flyway database migrations
     */
    private fun runMigrations(dbConfig: DatabaseConfig) {
        val flyway = Flyway.configure()
            .dataSource(dbConfig.jdbcUrl(), dbConfig.user, dbConfig.password)
            .locations("classpath:db/migration")
            .load()

        val result = flyway.migrate()
        println("  ✓ Applied ${result.migrationsExecuted} migrations")
    }

    /**
     * Create necessary directories
     */
    private fun createDirectories() {
        val dirs = listOf(
            "data/exports",
            "config"
        )

        dirs.forEach { dir ->
            val file = File(dir)
            if (!file.exists()) {
                file.mkdirs()
                println("  ✓ Created directory: $dir")
            } else {
                println("  - Directory exists: $dir")
            }
        }
    }

    /**
     * Create example configuration files if they don't exist
     */
    private fun createExampleConfigs() {
        println("\nConfiguration files:")

        // Check .env
        val envFile = File(".env")
        if (!envFile.exists()) {
            println("  ! Create .env file (copy from .env.example)")
        } else {
            println("  ✓ .env file exists")
        }

        // Check repositories.json
        val configFile = File("config/repositories.json")
        if (!configFile.exists()) {
            println("  ! Create config/repositories.json (copy from config/repositories.example.json)")
        } else {
            println("  ✓ config/repositories.json exists")
        }
    }
}

package com.metricmind.cli

import com.metricmind.config.ConfigLoader
import com.metricmind.database.DatabaseConnection
import com.metricmind.loader.DataLoader
import kotlinx.cli.Subcommand
import kotlinx.cli.ArgType
import kotlinx.cli.required

/**
 * CLI command to load JSON data into database
 */
class LoadCommand : Subcommand("load", "Load JSON data into PostgreSQL database") {
    private val jsonFile by argument(
        ArgType.String,
        description = "Path to JSON file"
    )

    override fun execute() {
        println("Loading data from JSON...")

        try {
            // Initialize database connection
            ConfigLoader.init()
            val dbConfig = ConfigLoader.loadDatabaseConfig()
            DatabaseConnection.init(dbConfig)

            val loader = DataLoader(jsonFile)
            val stats = loader.load()

            println("✓ Loading successful!")
            println("  Commits inserted: ${stats.commitsInserted}")
            println("  Commits skipped: ${stats.commitsSkipped}")
            println("  Errors: ${stats.errors}")
        } catch (e: Exception) {
            System.err.println("✗ Loading failed: ${e.message}")
            throw e
        } finally {
            DatabaseConnection.close()
        }
    }
}

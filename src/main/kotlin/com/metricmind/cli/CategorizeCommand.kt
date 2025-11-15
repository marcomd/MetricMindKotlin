package com.metricmind.cli

import com.metricmind.config.ConfigLoader
import com.metricmind.database.DatabaseConnection
import com.metricmind.processor.CommitCategorizer
import kotlinx.cli.Subcommand
import kotlinx.cli.ArgType
import kotlinx.cli.default

/**
 * CLI command to categorize commits
 */
class CategorizeCommand : Subcommand("categorize", "Categorize commits by business domain") {
    private val repoName by option(
        ArgType.String,
        fullName = "repo",
        description = "Process only this repository"
    )

    private val dryRun by option(
        ArgType.Boolean,
        fullName = "dry-run",
        description = "Preview changes without saving"
    ).default(false)

    override fun execute() {
        println("Categorizing commits...")

        try {
            // Initialize database connection
            ConfigLoader.init()
            val dbConfig = ConfigLoader.loadDatabaseConfig()
            DatabaseConnection.init(dbConfig)

            val categorizer = CommitCategorizer(repoName, dryRun)
            val stats = categorizer.categorize()

            println("✓ Categorization complete!")
            println("  Total commits: ${stats.total}")
            println("  Newly categorized: ${stats.categorized}")
            println("  Already categorized: ${stats.alreadyCategorized}")
        } catch (e: Exception) {
            System.err.println("✗ Categorization failed: ${e.message}")
            throw e
        } finally {
            DatabaseConnection.close()
        }
    }
}

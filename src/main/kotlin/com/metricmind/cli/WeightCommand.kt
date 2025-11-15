package com.metricmind.cli

import com.metricmind.config.ConfigLoader
import com.metricmind.database.DatabaseConnection
import com.metricmind.processor.WeightCalculator
import kotlinx.cli.Subcommand
import kotlinx.cli.ArgType
import kotlinx.cli.default

/**
 * CLI command to calculate commit weights
 */
class WeightCommand : Subcommand("weights", "Calculate commit weights (revert detection)") {
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
        println("Calculating commit weights...")

        try {
            // Initialize database connection
            ConfigLoader.init()
            val dbConfig = ConfigLoader.loadDatabaseConfig()
            DatabaseConnection.init(dbConfig)

            val calculator = WeightCalculator(repoName, dryRun)
            val stats = calculator.calculate()

            println("✓ Weight calculation complete!")
            println("  Total commits: ${stats.total}")
            println("  Reverts found: ${stats.revertsFound}")
            println("  Commits zeroed: ${stats.commitsZeroed}")
            println("  Commits restored: ${stats.commitsRestored}")
        } catch (e: Exception) {
            System.err.println("✗ Weight calculation failed: ${e.message}")
            throw e
        } finally {
            DatabaseConnection.close()
        }
    }
}

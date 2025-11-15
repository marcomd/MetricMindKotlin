package com.metricmind.cli

import com.metricmind.cleaner.RepositoryCleaner
import com.metricmind.config.ConfigLoader
import com.metricmind.database.DatabaseConnection
import kotlinx.cli.Subcommand
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required

/**
 * CLI command to clean repository data
 */
class CleanCommand : Subcommand("clean", "Clean repository data from database") {
    private val repoName by argument(
        ArgType.String,
        description = "Repository name to clean"
    )

    private val force by option(
        ArgType.Boolean,
        fullName = "force",
        description = "Skip confirmation prompt"
    ).default(false)

    private val dryRun by option(
        ArgType.Boolean,
        fullName = "dry-run",
        description = "Preview changes without deleting"
    ).default(false)

    private val deleteRepo by option(
        ArgType.Boolean,
        fullName = "delete-repo",
        description = "Also delete repository record (not just commits)"
    ).default(false)

    override fun execute() {
        println("Cleaning repository: $repoName...")

        try {
            // Initialize database connection
            ConfigLoader.init()
            val dbConfig = ConfigLoader.loadDatabaseConfig()
            DatabaseConnection.init(dbConfig)

            val cleaner = RepositoryCleaner(repoName, force, dryRun, deleteRepo)
            val stats = cleaner.clean()

            println("✓ Cleanup complete!")
            println("  Commits deleted: ${stats.commitsDeleted}")
            println("  Repository deleted: ${stats.repositoryDeleted}")
        } catch (e: Exception) {
            System.err.println("✗ Cleanup failed: ${e.message}")
            throw e
        } finally {
            DatabaseConnection.close()
        }
    }
}

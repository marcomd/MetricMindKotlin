package com.metricmind.cleaner

import com.metricmind.database.Commits
import com.metricmind.database.DatabaseConnection
import com.metricmind.database.Repositories
import mu.KotlinLogging
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

private val logger = KotlinLogging.logger {}

/**
 * Statistics for cleanup operations
 */
data class CleanStats(
    var commitsDeleted: Int = 0,
    var repositoryDeleted: Boolean = false
)

/**
 * Handles cleanup operations for repositories
 */
class RepositoryCleaner(
    private val repoName: String,
    private val force: Boolean = false,
    private val dryRun: Boolean = false,
    private val deleteRepo: Boolean = false
) {
    private val stats = CleanStats()

    /**
     * Execute cleanup operation
     */
    fun clean(): CleanStats {
        logger.info { "Starting cleanup for repository: $repoName" }

        if (dryRun) {
            logger.info { "DRY RUN MODE - No changes will be saved" }
        }

        // Ensure database is initialized
        if (!DatabaseConnection.isInitialized()) {
            throw IllegalStateException("Database not initialized. Call DatabaseConnection.init() first.")
        }

        transaction {
            val repoInfo = findRepository()

            if (repoInfo == null) {
                listAvailableRepositories()
                throw IllegalArgumentException("Repository not found: $repoName")
            }

            showDeletionSummary(repoInfo)

            if (!force && !dryRun) {
                if (!confirmDeletion()) {
                    logger.info { "Cleanup cancelled by user" }
                    return@transaction
                }
            }

            if (!dryRun) {
                deleteData(repoInfo.id)
            }
        }

        printSummary()
        return stats
    }

    /**
     * Find repository by name
     */
    private fun findRepository(): RepoInfo? {
        val result = Repositories.select { Repositories.name eq repoName }.singleOrNull()
        return result?.let {
            val repoId = it[Repositories.id].value
            val commitsCount = Commits.select { Commits.repositoryId eq repoId }.count().toInt()

            RepoInfo(
                id = repoId,
                name = it[Repositories.name],
                url = it[Repositories.url],
                commitsCount = commitsCount
            )
        }
    }

    /**
     * Show what will be deleted
     */
    private fun showDeletionSummary(info: RepoInfo) {
        println("\n=== Deletion Summary ===")
        println("Repository: ${info.name}")
        println("URL: ${info.url ?: "N/A"}")
        println("Commits to delete: ${info.commitsCount}")
        if (deleteRepo) {
            println("Repository record: WILL BE DELETED")
        } else {
            println("Repository record: will be kept (use --delete-repo to remove)")
        }
        println("========================\n")
    }

    /**
     * Interactive confirmation prompt
     */
    private fun confirmDeletion(): Boolean {
        print("Are you sure you want to delete this data? (yes/no): ")
        val response = readlnOrNull()?.trim()?.lowercase()
        return response == "yes" || response == "y"
    }

    /**
     * Delete data from database
     */
    private fun deleteData(repoId: Int) {
        logger.info { "Deleting data for repository ID: $repoId" }

        // Delete commits
        val deletedCommits = Commits.deleteWhere { Commits.repositoryId eq repoId }
        stats.commitsDeleted = deletedCommits
        logger.info { "Deleted $deletedCommits commits" }

        // Optionally delete repository record
        if (deleteRepo) {
            val deletedRepos = Repositories.deleteWhere { Repositories.id eq repoId }
            stats.repositoryDeleted = deletedRepos > 0
            logger.info { "Deleted repository record" }
        }
    }

    /**
     * List available repositories
     */
    private fun listAvailableRepositories() {
        val repos = Repositories
            .slice(Repositories.name)
            .selectAll()
            .map { it[Repositories.name] }

        if (repos.isNotEmpty()) {
            println("\nAvailable repositories:")
            repos.forEach { name ->
                println("  - $name")
            }
            println()
        }
    }

    /**
     * Print cleanup summary
     */
    private fun printSummary() {
        println("\n=== Cleanup Summary ===")
        println("Commits deleted: ${stats.commitsDeleted}")
        println("Repository deleted: ${if (stats.repositoryDeleted) "Yes" else "No"}")
        if (dryRun) {
            println("(DRY RUN - no changes saved)")
        }
        println("=======================\n")

        logger.info {
            "Cleanup complete: ${stats.commitsDeleted} commits deleted, " +
                    "repository ${if (stats.repositoryDeleted) "deleted" else "kept"}"
        }
    }

    /**
     * Data class for repository information
     */
    private data class RepoInfo(
        val id: Int,
        val name: String,
        val url: String?,
        val commitsCount: Int
    )
}

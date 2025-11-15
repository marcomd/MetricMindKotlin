package com.metricmind.processor

import com.metricmind.database.Commits
import com.metricmind.database.DatabaseConnection
import com.metricmind.database.Repositories
import mu.KotlinLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

private val logger = KotlinLogging.logger {}

/**
 * Statistics for categorization process
 */
data class CategorizationStats(
    var total: Int = 0,
    var categorized: Int = 0,
    var alreadyCategorized: Int = 0,
    var updated: Int = 0
)

/**
 * Extracts business domain categories from commit subjects
 */
class CommitCategorizer(
    private val repoName: String? = null,
    private val dryRun: Boolean = false
) {
    private val stats = CategorizationStats()

    companion object {
        // Common commit verbs to exclude from category extraction
        private val EXCLUDED_WORDS = setOf(
            "MERGE", "FIX", "ADD", "UPDATE", "REMOVE", "DELETE",
            "FEAT", "CHORE", "DOCS", "STYLE", "REFACTOR", "TEST", "PERF"
        )

        /**
         * Extract category from commit subject using three patterns
         */
        fun extractCategory(subject: String?): String? {
            if (subject.isNullOrBlank()) return null

            // Pattern 1: Pipe delimiter - "BILLING | Description"
            if (subject.contains(" | ")) {
                val category = subject.split(" | ", limit = 2)[0].trim().uppercase()
                if (category.isNotBlank()) return category
            }

            // Pattern 2: Square brackets - "[CS] Description"
            val bracketMatch = Regex("""^\[([^\]]+)\]""").find(subject)
            if (bracketMatch != null) {
                val category = bracketMatch.groupValues[1].trim().uppercase()
                if (category.isNotBlank()) return category
            }

            // Pattern 3: First uppercase word - "BILLING Description"
            val firstWord = subject.trim().split(Regex("""\s+"""), limit = 2)[0]
            if (firstWord.length >= 2 && firstWord == firstWord.uppercase()) {
                if (firstWord !in EXCLUDED_WORDS) {
                    return firstWord
                }
            }

            return null
        }
    }

    /**
     * Process commits and update categories
     */
    fun categorize(): CategorizationStats {
        logger.info { "Starting commit categorization${if (repoName != null) " for repository: $repoName" else " for all repositories"}" }

        if (dryRun) {
            logger.info { "DRY RUN MODE - No changes will be saved" }
        }

        // Ensure database is initialized
        if (!DatabaseConnection.isInitialized()) {
            throw IllegalStateException("Database not initialized. Call DatabaseConnection.init() first.")
        }

        transaction {
            val commits = fetchCommits()
            stats.total = commits.size

            commits.forEach { (id, hash, subject, existingCategory) ->
                if (existingCategory != null) {
                    stats.alreadyCategorized++
                } else {
                    val category = extractCategory(subject)
                    if (category != null) {
                        if (!dryRun) {
                            updateCommitCategory(id, category)
                        }
                        stats.categorized++
                        stats.updated++
                        logger.debug { "Commit $hash: '$subject' → $category" }
                    }
                }
            }
        }

        printSummary()
        return stats
    }

    /**
     * Fetch commits to process
     */
    private fun fetchCommits(): List<CommitData> {
        val query = if (repoName != null) {
            // Filter by repository name
            Commits
                .innerJoin(Repositories)
                .slice(Commits.id, Commits.hash, Commits.subject, Commits.category)
                .select { Repositories.name eq repoName }
        } else {
            // All commits
            Commits
                .slice(Commits.id, Commits.hash, Commits.subject, Commits.category)
                .selectAll()
        }

        return query.map {
            CommitData(
                id = it[Commits.id].value,
                hash = it[Commits.hash],
                subject = it[Commits.subject],
                category = it[Commits.category]
            )
        }
    }

    /**
     * Update commit category
     */
    private fun updateCommitCategory(commitId: Int, category: String) {
        Commits.update({ Commits.id eq commitId }) {
            it[Commits.category] = category
        }
    }

    /**
     * Print categorization summary
     */
    private fun printSummary() {
        val coveragePercent = if (stats.total > 0) {
            (stats.alreadyCategorized + stats.categorized).toDouble() / stats.total * 100
        } else {
            0.0
        }

        println("\n=== Categorization Summary ===")
        println("Total commits: ${stats.total}")
        println("Already categorized: ${stats.alreadyCategorized}")
        println("Newly categorized: ${stats.categorized}")
        println("Coverage: ${"%.1f".format(coveragePercent)}%")
        if (dryRun) {
            println("(DRY RUN - no changes saved)")
        }
        println("==============================\n")

        logger.info {
            "Categorization complete: ${stats.categorized} newly categorized, " +
                    "${stats.alreadyCategorized} already categorized, " +
                    "coverage: ${"%.1f".format(coveragePercent)}%"
        }
    }

    /**
     * Data class for commit information
     */
    private data class CommitData(
        val id: Int,
        val hash: String,
        val subject: String,
        val category: String?
    )
}

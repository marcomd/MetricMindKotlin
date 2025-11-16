package com.metricmind.processor

import com.metricmind.database.Commits
import com.metricmind.database.DatabaseConnection
import com.metricmind.database.Repositories
import mu.KotlinLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

private val logger = KotlinLogging.logger {}

/**
 * Statistics for weight calculation
 */
data class WeightStats(
    var total: Int = 0,
    var revertsFound: Int = 0,
    var commitsZeroed: Int = 0
)

/**
 * Calculates commit weights based on revert/unrevert patterns
 */
class WeightCalculator(
    private val repoName: String? = null,
    private val dryRun: Boolean = false
) {
    private val stats = WeightStats()

    companion object {
        // Patterns for PR/MR numbers (with or without parentheses)
        private val PR_PATTERN_GITLAB = Regex("""!(\d+)""") // !12345 or (!12345)
        private val PR_PATTERN_GITHUB = Regex("""#(\d+)""")  // #12345 or (#12345)

        /**
         * Extract PR/MR numbers from commit subject
         * Returns list of PR/MR identifiers like "!12345" or "#12345"
         * Matches both with and without parentheses: !123 and (!123)
         */
        fun extractPrNumbers(subject: String): List<String> {
            val numbers = mutableSetOf<String>()

            // Find GitLab style !12345 or (!12345)
            PR_PATTERN_GITLAB.findAll(subject).forEach {
                numbers.add("!${it.groupValues[1]}")
            }

            // Find GitHub style #12345 or (#12345)
            PR_PATTERN_GITHUB.findAll(subject).forEach {
                numbers.add("#${it.groupValues[1]}")
            }

            return numbers.toList()
        }

        /**
         * Check if subject indicates a revert commit
         */
        fun isRevertCommit(subject: String): Boolean {
            val lower = subject.lowercase()
            return lower.contains("revert") && !lower.contains("unrevert")
        }

        /**
         * Check if subject indicates an unrevert commit
         */
        fun isUnrevertCommit(subject: String): Boolean {
            return subject.lowercase().contains("unrevert")
        }
    }

    /**
     * Execute weight calculation with single-pass algorithm
     *
     * This processes only revert commits, setting weight=0 for both the revert
     * and its original commits. Unrevert commits are NOT processed - they stay
     * at their default weight=100, while the original commits remain at weight=0.
     * This prevents double-counting work.
     */
    fun calculate(): WeightStats {
        logger.info { "Starting weight calculation${if (repoName != null) " for repository: $repoName" else " for all repositories"}" }

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

            // Single pass: Process only reverts (not unreverts)
            processReverts(commits)
        }

        printSummary()
        return stats
    }

    /**
     * Fetch commits to process
     */
    private fun fetchCommits(): List<CommitData> {
        val query = if (repoName != null) {
            Commits
                .innerJoin(Repositories)
                .slice(Commits.id, Commits.hash, Commits.subject, Commits.weight, Commits.repositoryId)
                .select { Repositories.name eq repoName }
        } else {
            Commits
                .slice(Commits.id, Commits.hash, Commits.subject, Commits.weight, Commits.repositoryId)
                .selectAll()
        }

        return query.map {
            CommitData(
                id = it[Commits.id].value,
                repositoryId = it[Commits.repositoryId].value,
                hash = it[Commits.hash],
                subject = it[Commits.subject],
                weight = it[Commits.weight]
            )
        }
    }

    /**
     * Process revert commits (single pass)
     *
     * For each revert commit:
     * 1. Set weight=0 for the revert commit itself
     * 2. Set weight=0 for all original commits matching the PR/MR number
     *
     * Note: "Unrevert" commits are excluded by isRevertCommit() and remain at weight=100
     */
    private fun processReverts(commits: List<CommitData>) {
        logger.info { "Processing reverts..." }

        commits.filter { isRevertCommit(it.subject) }.forEach { revertCommit ->
            stats.revertsFound++

            // Extract PR numbers from revert commit
            val prNumbers = extractPrNumbers(revertCommit.subject)

            if (prNumbers.isNotEmpty()) {
                // Find original commits with matching PR numbers
                val originalCommits = commits.filter { commit ->
                    commit.id != revertCommit.id &&
                            commit.repositoryId == revertCommit.repositoryId &&
                            prNumbers.any { pr -> commit.subject.contains(pr) }
                }

                // Set weight=0 for revert commit itself
                if (revertCommit.weight != 0) {
                    if (!dryRun) {
                        setWeight(revertCommit.id, 0)
                    }
                    stats.commitsZeroed++
                    logger.debug { "Revert: ${revertCommit.hash} - ${revertCommit.subject}" }
                }

                // Set weight=0 for original commits
                originalCommits.forEach { original ->
                    if (original.weight != 0) {
                        if (!dryRun) {
                            setWeight(original.id, 0)
                        }
                        stats.commitsZeroed++
                        logger.debug { "  → Zeroed original: ${original.hash} - ${original.subject}" }
                    }
                }
            }
        }
    }

    /**
     * Update commit weight
     */
    private fun setWeight(commitId: Int, weight: Int) {
        Commits.update({ Commits.id eq commitId }) {
            it[Commits.weight] = weight
        }
    }

    /**
     * Print weight calculation summary
     */
    private fun printSummary() {
        println("\n=== Weight Calculation Summary ===")
        println("Total commits: ${stats.total}")
        println("Reverts found: ${stats.revertsFound}")
        println("Commits zeroed (weight=0): ${stats.commitsZeroed}")
        if (dryRun) {
            println("(DRY RUN - no changes saved)")
        }
        println("==================================\n")

        logger.info {
            "Weight calculation complete: ${stats.revertsFound} reverts, " +
                    "${stats.commitsZeroed} commits zeroed"
        }
    }

    /**
     * Data class for commit information
     */
    private data class CommitData(
        val id: Int,
        val repositoryId: Int,
        val hash: String,
        val subject: String,
        val weight: Int
    )
}

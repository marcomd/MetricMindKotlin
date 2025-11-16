package com.metricmind.ai

import com.metricmind.config.AiConfig
import com.metricmind.database.Categories
import com.metricmind.database.Commits
import com.metricmind.database.DatabaseConnection
import com.metricmind.database.Repositories
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

private val logger = KotlinLogging.logger {}

/**
 * Statistics for AI categorization
 */
data class AiCategorizationStats(
    var total: Int = 0,
    var processed: Int = 0,
    var categorized: Int = 0,
    var errors: Int = 0,
    var skipped: Int = 0,
    var newCategoriesCreated: Int = 0
)

/**
 * AI-powered commit categorizer using LLM clients
 */
class AiCategorizer(
    private val config: AiConfig,
    private val repoName: String? = null,
    private val batchSize: Int = 50,
    private val dryRun: Boolean = false
) {
    private val stats = AiCategorizationStats()
    private lateinit var client: BaseLlmClient

    /**
     * Execute AI categorization
     *
     * @return Statistics for the categorization run
     */
    fun categorize(): AiCategorizationStats {
        logger.info { "Starting AI categorization${if (repoName != null) " for repository: $repoName" else " for all repositories"}" }

        if (dryRun) {
            logger.info { "DRY RUN MODE - No changes will be saved" }
        }

        // Ensure database is initialized
        if (!DatabaseConnection.isInitialized()) {
            throw IllegalStateException("Database not initialized. Call DatabaseConnection.init() first.")
        }

        // Validate configuration
        val (isValid, errors) = LlmClientFactory.validateConfiguration(config)
        if (!isValid) {
            throw LlmException.ConfigurationError("Invalid AI configuration: ${errors.joinToString(", ")}")
        }

        try {
            // Create LLM client
            client = LlmClientFactory.create(config)
            logger.info { "[AiCategorizer] Using ${config.provider} provider" }

            // Get existing categories for consistency
            val existingCategories = fetchExistingCategories()
            logger.info { "[AiCategorizer] Found ${existingCategories.size} existing categories" }

            // Fetch commits to categorize
            val commits = fetchCommitsToCategorize()
            stats.total = commits.size
            logger.info { "[AiCategorizer] Found ${commits.size} commits to categorize" }

            if (commits.isEmpty()) {
                logger.info { "No commits to categorize" }
                return stats
            }

            // Process in batches
            commits.chunked(batchSize).forEachIndexed { batchIndex, batch ->
                val batchNum = batchIndex + 1
                val totalBatches = (commits.size + batchSize - 1) / batchSize
                logger.info { "[AiCategorizer] Processing batch $batchNum/$totalBatches (${batch.size} commits)" }

                processBatch(batch, existingCategories)
            }

        } catch (e: Exception) {
            logger.error(e) { "[AiCategorizer] Fatal error during categorization" }
            throw e
        } finally {
            // Close client if it's a closeable type
            if (::client.isInitialized) {
                val currentClient = client
                when (currentClient) {
                    is GeminiClient -> currentClient.close()
                    is OllamaClient -> currentClient.close()
                }
            }
        }

        printSummary()
        return stats
    }

    /**
     * Fetch existing categories from database
     */
    private fun fetchExistingCategories(): MutableList<String> {
        return transaction {
            Categories
                .slice(Categories.name)
                .selectAll()
                .map { it[Categories.name] }
                .toMutableList()
        }
    }

    /**
     * Fetch commits that need categorization
     *
     * Targets commits without a category or without AI confidence
     */
    private fun fetchCommitsToCategorize(): List<CommitInfo> {
        return transaction {
            val query = if (repoName != null) {
                Commits
                    .innerJoin(Repositories)
                    .slice(
                        Commits.id,
                        Commits.hash,
                        Commits.subject,
                        Commits.category,
                        Commits.aiConfidence,
                        Commits.repositoryId
                    )
                    .select {
                        (Repositories.name eq repoName) and
                        (Commits.category.isNull() or Commits.aiConfidence.isNull())
                    }
            } else {
                Commits
                    .slice(
                        Commits.id,
                        Commits.hash,
                        Commits.subject,
                        Commits.category,
                        Commits.aiConfidence,
                        Commits.repositoryId
                    )
                    .select { Commits.category.isNull() or Commits.aiConfidence.isNull() }
            }

            query.map {
                CommitInfo(
                    id = it[Commits.id].value,
                    hash = it[Commits.hash],
                    subject = it[Commits.subject],
                    currentCategory = it[Commits.category],
                    aiConfidence = it[Commits.aiConfidence],
                    repositoryId = it[Commits.repositoryId].value
                )
            }
        }
    }

    /**
     * Process a batch of commits
     */
    private fun processBatch(batch: List<CommitInfo>, existingCategories: MutableList<String>) {
        batch.forEach { commit ->
            try {
                processCommit(commit, existingCategories)
            } catch (e: Exception) {
                logger.error(e) { "[AiCategorizer] Error processing commit ${commit.hash}: ${e.message}" }
                stats.errors++
            }
        }
    }

    /**
     * Process a single commit
     */
    private fun processCommit(commit: CommitInfo, existingCategories: MutableList<String>) {
        stats.processed++

        // Skip if already AI-categorized with high confidence
        if (commit.currentCategory != null && commit.aiConfidence != null && commit.aiConfidence >= 80) {
            logger.debug { "[AiCategorizer] Skipping ${commit.hash} (already categorized with confidence ${commit.aiConfidence})" }
            stats.skipped++
            return
        }

        try {
            // Call LLM (blocking for simplicity - could be made async)
            val result = runBlocking {
                client.categorize(
                    commitData = com.metricmind.ai.CommitData(
                        hash = commit.hash,
                        subject = commit.subject,
                        files = null  // File list could be added here
                    ),
                    existingCategories = existingCategories
                )
            }

            logger.info { "[AiCategorizer] ${commit.hash.take(8)}: ${result.category} (${result.confidence}%)" }

            // Save new category if needed
            if (result.category !in existingCategories) {
                if (!dryRun) {
                    upsertCategory(result.category, result.reason)
                    existingCategories.add(result.category)
                    stats.newCategoriesCreated++
                }
                logger.info { "[AiCategorizer] Created new category: ${result.category}" }
            }

            // Update commit
            if (!dryRun) {
                updateCommit(commit.id, result.category, result.confidence)
                stats.categorized++
            }

        } catch (e: LlmException) {
            logger.error { "[AiCategorizer] LLM error for ${commit.hash}: ${e.message}" }
            stats.errors++
        }
    }

    /**
     * Insert or update category
     */
    private fun upsertCategory(name: String, description: String) {
        transaction {
            // Try to find existing
            val existing = Categories.select { Categories.name eq name }.singleOrNull()

            if (existing == null) {
                // Insert new
                Categories.insert {
                    it[Categories.name] = name
                    it[Categories.description] = description
                    it[usageCount] = 0
                }
            } else {
                // Update description if needed
                Categories.update({ Categories.name eq name }) {
                    it[Categories.description] = description
                }
            }
        }
    }

    /**
     * Update commit with category and confidence
     */
    private fun updateCommit(commitId: Int, category: String, confidence: Int) {
        transaction {
            Commits.update({ Commits.id eq commitId }) {
                it[Commits.category] = category
                it[aiConfidence] = confidence.toShort()
            }

            // Increment category usage count
            Categories.update({ Categories.name eq category }) {
                with(SqlExpressionBuilder) {
                    it[usageCount] = usageCount + 1
                }
            }
        }
    }

    /**
     * Print categorization summary
     */
    private fun printSummary() {
        println("\n=== AI Categorization Summary ===")
        println("Provider: ${config.provider}")
        println("Total commits: ${stats.total}")
        println("Processed: ${stats.processed}")
        println("Categorized: ${stats.categorized}")
        println("Skipped: ${stats.skipped}")
        println("Errors: ${stats.errors}")
        println("New categories created: ${stats.newCategoriesCreated}")
        if (dryRun) {
            println("(DRY RUN - no changes saved)")
        }
        println("==================================\n")

        logger.info {
            "AI categorization complete: ${stats.categorized} categorized, " +
                    "${stats.errors} errors, ${stats.newCategoriesCreated} new categories"
        }
    }

    /**
     * Internal data class for commit information
     */
    private data class CommitInfo(
        val id: Int,
        val hash: String,
        val subject: String,
        val currentCategory: String?,
        val aiConfidence: Short?,
        val repositoryId: Int
    )
}

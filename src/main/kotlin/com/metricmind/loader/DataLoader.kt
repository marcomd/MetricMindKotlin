package com.metricmind.loader

import com.metricmind.database.Commits
import com.metricmind.database.DatabaseConnection
import com.metricmind.database.Repositories
import com.metricmind.models.Commit
import com.metricmind.models.ExtractionResult
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.api.ExposedConnection
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

/**
 * Statistics tracked during data loading
 */
data class LoadStats(
    var reposCreated: Int = 0,
    var reposUpdated: Int = 0,
    var commitsInserted: Int = 0,
    var commitsSkipped: Int = 0,
    var errors: Int = 0
)

/**
 * Loads extracted JSON data into PostgreSQL database
 */
class DataLoader(private val jsonFile: String) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val stats = LoadStats()

    companion object {
        private const val BATCH_SIZE = 100
    }

    /**
     * Execute the complete data loading workflow
     */
    fun load(): LoadStats {
        logger.info { "Loading data from $jsonFile" }

        validateJsonFile()
        val extractionResult = loadJsonData()

        // Ensure database is initialized
        if (!DatabaseConnection.isInitialized()) {
            throw IllegalStateException("Database not initialized. Call DatabaseConnection.init() first.")
        }

        transaction {
            val repoId = loadRepository(extractionResult)
            loadCommits(repoId, extractionResult.commits)
            refreshMaterializedViews()
        }

        printSummary()
        return stats
    }

    /**
     * Validate JSON file exists
     */
    private fun validateJsonFile() {
        val file = File(jsonFile)
        if (!file.exists()) {
            throw IllegalArgumentException("JSON file not found: $jsonFile")
        }
    }

    /**
     * Load and parse JSON data
     */
    private fun loadJsonData(): ExtractionResult {
        val file = File(jsonFile)
        val content = file.readText()
        return json.decodeFromString(ExtractionResult.serializer(), content)
    }

    /**
     * Load or update repository record
     */
    private fun loadRepository(data: ExtractionResult): Int {
        val repoName = data.repository
        val lastExtracted = parseTimestamp(data.extractionDate)

        // Check if repository exists
        val existing = Repositories.select { Repositories.name eq repoName }.singleOrNull()

        return if (existing != null) {
            // Update existing repository
            val repoId = existing[Repositories.id].value
            Repositories.update({ Repositories.id eq repoId }) {
                it[lastExtractedAt] = lastExtracted
                it[updatedAt] = Instant.now()
            }
            stats.reposUpdated++
            logger.info { "Updated repository: $repoName (ID: $repoId)" }
            repoId
        } else {
            // Insert new repository
            val repoId = Repositories.insertAndGetId {
                it[name] = repoName
                it[url] = data.repositoryPath
                it[lastExtractedAt] = lastExtracted
            }.value
            stats.reposCreated++
            logger.info { "Created repository: $repoName (ID: $repoId)" }
            repoId
        }
    }

    /**
     * Load commits in batches with duplicate handling
     */
    private fun loadCommits(repoId: Int, commits: List<Commit>) {
        logger.info { "Loading ${commits.size} commits in batches of $BATCH_SIZE" }

        commits.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
            var batchInserted = 0
            var batchSkipped = 0

            batch.forEach { commit ->
                try {
                    // Check if commit already exists
                    val exists = Commits.select {
                        (Commits.repositoryId eq repoId) and (Commits.hash eq commit.hash)
                    }.count() > 0

                    if (!exists) {
                        Commits.insert {
                            it[repositoryId] = repoId
                            it[hash] = commit.hash
                            it[commitDate] = parseTimestamp(commit.date)
                            it[authorName] = commit.authorName
                            it[authorEmail] = commit.authorEmail
                            it[subject] = commit.subject
                            it[linesAdded] = commit.linesAdded
                            it[linesDeleted] = commit.linesDeleted
                            it[filesChanged] = commit.filesChanged
                            it[weight] = commit.weight
                            it[aiTools] = commit.aiTools
                            it[category] = commit.category
                        }
                        batchInserted++
                    } else {
                        batchSkipped++
                    }
                } catch (e: ExposedSQLException) {
                    // Handle unique constraint violations
                    if (e.message?.contains("unique_commit_per_repo") == true) {
                        batchSkipped++
                    } else {
                        logger.error(e) { "Error inserting commit ${commit.hash}" }
                        stats.errors++
                    }
                }
            }

            stats.commitsInserted += batchInserted
            stats.commitsSkipped += batchSkipped

            if ((batchIndex + 1) % 10 == 0) {
                logger.info { "Processed ${(batchIndex + 1) * BATCH_SIZE} commits..." }
            }
        }
    }

    /**
     * Refresh materialized views
     */
    private fun refreshMaterializedViews() {
        logger.info { "Refreshing materialized views..." }

        try {
            TransactionManager.current().exec("REFRESH MATERIALIZED VIEW mv_monthly_stats_by_repo")
            TransactionManager.current().exec("REFRESH MATERIALIZED VIEW mv_monthly_category_stats")
            logger.info { "Materialized views refreshed successfully" }
        } catch (e: Exception) {
            logger.warn { "Failed to refresh materialized views: ${e.message}" }
        }
    }

    /**
     * Parse timestamp string to Instant
     */
    private fun parseTimestamp(timestamp: String): Instant {
        return try {
            Instant.parse(timestamp)
        } catch (e: Exception) {
            // Try parsing as LocalDateTime
            try {
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                LocalDateTime.parse(timestamp, formatter).toInstant(ZoneOffset.UTC)
            } catch (e2: Exception) {
                logger.warn { "Failed to parse timestamp: $timestamp, using current time" }
                Instant.now()
            }
        }
    }

    /**
     * Print loading summary
     */
    private fun printSummary() {
        println("\n=== Data Loading Summary ===")
        println("Repositories created: ${stats.reposCreated}")
        println("Repositories updated: ${stats.reposUpdated}")
        println("Commits inserted: ${stats.commitsInserted}")
        println("Commits skipped (duplicates): ${stats.commitsSkipped}")
        println("Errors: ${stats.errors}")
        println("============================\n")

        logger.info {
            "Loading complete: ${stats.commitsInserted} commits inserted, " +
                    "${stats.commitsSkipped} skipped, ${stats.errors} errors"
        }
    }
}

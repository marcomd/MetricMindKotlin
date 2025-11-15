package com.metricmind.extractor

import com.metricmind.models.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

/**
 * Extracts git commit data from a repository
 */
class GitExtractor(
    private val fromDate: String,
    private val toDate: String,
    private val outputFile: String,
    private val repoName: String?,
    private val repoPath: String
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * Execute the complete extraction workflow
     */
    fun extract(): ExtractionResult {
        logger.info { "Starting git extraction from $repoPath" }

        validateGitRepo()
        validateDateRange()

        val commits = extractCommits()
        val detectedRepoName = repoName ?: detectRepoName()

        val result = ExtractionResult(
            repository = detectedRepoName,
            repositoryPath = File(repoPath).absolutePath,
            extractionDate = Instant.now().toString(),
            dateRange = DateRange(fromDate, toDate),
            summary = calculateSummary(commits),
            commits = commits
        )

        writeOutput(result)

        logger.info {
            "Extraction complete: ${commits.size} commits from $detectedRepoName " +
                    "(${result.summary.totalLinesAdded} added, ${result.summary.totalLinesDeleted} deleted)"
        }

        return result
    }

    /**
     * Validate that the path is a git repository
     */
    private fun validateGitRepo() {
        val gitDir = File(repoPath, ".git")
        if (!gitDir.exists() || !gitDir.isDirectory) {
            throw IllegalArgumentException("Not a git repository: $repoPath")
        }
    }

    /**
     * Validate date range format (basic check)
     */
    private fun validateDateRange() {
        if (fromDate.isBlank() || toDate.isBlank()) {
            throw IllegalArgumentException("Date range cannot be empty")
        }
    }

    /**
     * Detect repository name from git remote or directory name
     */
    private fun detectRepoName(): String {
        // Try to get from git remote
        try {
            val remoteUrl = executeGitCommand(listOf("git", "-C", repoPath, "config", "--get", "remote.origin.url"))
            if (remoteUrl.isNotBlank()) {
                // Extract name from URL like https://github.com/user/repo.git or git@github.com:user/repo.git
                val name = remoteUrl.trim()
                    .substringAfterLast("/")
                    .substringAfterLast(":")
                    .removeSuffix(".git")
                if (name.isNotBlank()) return name
            }
        } catch (e: Exception) {
            logger.warn { "Could not get git remote: ${e.message}" }
        }

        // Fall back to directory name
        return File(repoPath).name
    }

    /**
     * Extract commits from git repository
     */
    private fun extractCommits(): List<Commit> {
        logger.info { "Extracting commits from $fromDate to $toDate" }

        val output = executeGitCommand(
            listOf(
                "git", "-C", repoPath,
                "log",
                "--since=$fromDate",
                "--until=$toDate",
                "--numstat",
                "--pretty=format:COMMIT|%H|%ai|%an|%ae|%s|BODY|%b|BODYEND|"
            )
        )

        return parseGitOutput(output)
    }

    /**
     * Execute git command and return output
     */
    private fun executeGitCommand(command: List<String>): String {
        val processBuilder = ProcessBuilder(command)
        processBuilder.redirectErrorStream(true)

        val process = processBuilder.start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw RuntimeException("Git command failed with exit code $exitCode: ${command.joinToString(" ")}\n$output")
        }

        return output
    }

    /**
     * Parse git log output into structured commit data
     */
    private fun parseGitOutput(output: String): List<Commit> {
        val commits = mutableListOf<Commit>()
        val commitBlocks = output.split("COMMIT|").filter { it.isNotBlank() }

        for (block in commitBlocks) {
            try {
                val commit = parseCommitBlock(block)
                commits.add(commit)
            } catch (e: Exception) {
                logger.warn { "Failed to parse commit block: ${e.message}" }
            }
        }

        return commits
    }

    /**
     * Parse a single commit block
     */
    private fun parseCommitBlock(block: String): Commit {
        // Split header and file stats
        val parts = block.split("\n")
        val header = parts[0]

        // Parse header: hash|date|author_name|author_email|subject|BODY|body|BODYEND|
        val headerParts = header.split("|")
        if (headerParts.size < 6) {
            throw IllegalArgumentException("Invalid commit header format")
        }

        val hash = headerParts[0].trim()
        val date = headerParts[1].trim()
        val authorName = headerParts[2].trim()
        val authorEmail = headerParts[3].trim()
        val subject = headerParts[4].trim()

        // Extract body (between BODY| and |BODYEND|)
        val bodyStart = header.indexOf("BODY|")
        val bodyEnd = header.indexOf("|BODYEND|")
        val body = if (bodyStart >= 0 && bodyEnd > bodyStart) {
            header.substring(bodyStart + 5, bodyEnd).trim()
        } else {
            null
        }

        // Extract AI tools from body
        val aiTools = AiToolsExtractor.extract(body)

        // Parse file stats (lines after header)
        val fileStats = mutableListOf<CommitFile>()
        var linesAdded = 0
        var linesDeleted = 0

        for (i in 1 until parts.size) {
            val line = parts[i].trim()
            if (line.isEmpty()) continue

            // Format: added\tdeleted\tfilename
            val statParts = line.split("\t")
            if (statParts.size >= 3) {
                val added = statParts[0].toIntOrNull() ?: 0
                val deleted = statParts[1].toIntOrNull() ?: 0
                val filename = statParts[2]

                // Skip binary files (marked with "-")
                if (statParts[0] != "-" && statParts[1] != "-") {
                    linesAdded += added
                    linesDeleted += deleted
                    fileStats.add(CommitFile(filename, added, deleted))
                }
            }
        }

        return Commit(
            hash = hash,
            date = formatDate(date),
            authorName = authorName,
            authorEmail = authorEmail,
            subject = subject,
            body = body,
            linesAdded = linesAdded,
            linesDeleted = linesDeleted,
            filesChanged = fileStats.size,
            weight = 100,
            aiTools = aiTools,
            category = null,
            files = fileStats
        )
    }

    /**
     * Format git date to ISO 8601
     */
    private fun formatDate(gitDate: String): String {
        return try {
            // Git format: 2025-11-15 10:30:00 +0000
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z")
            val dateTime = LocalDateTime.parse(gitDate.substring(0, 19), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            dateTime.toInstant(ZoneOffset.UTC).toString()
        } catch (e: Exception) {
            logger.warn { "Failed to parse date: $gitDate, using as-is" }
            gitDate
        }
    }

    /**
     * Calculate summary statistics from commits
     */
    private fun calculateSummary(commits: List<Commit>): Summary {
        return Summary(
            totalCommits = commits.size,
            totalLinesAdded = commits.sumOf { it.linesAdded },
            totalLinesDeleted = commits.sumOf { it.linesDeleted },
            totalFilesChanged = commits.sumOf { it.filesChanged },
            uniqueAuthors = commits.map { it.authorEmail }.distinct().size
        )
    }

    /**
     * Write extraction result to JSON file
     */
    private fun writeOutput(result: ExtractionResult) {
        val file = File(outputFile)

        // Create parent directories if needed
        file.parentFile?.mkdirs()

        val jsonString = json.encodeToString(result)
        file.writeText(jsonString)

        logger.info { "Output written to: $outputFile" }
    }
}

package com.metricmind.cli

import com.metricmind.config.ConfigLoader
import com.metricmind.extractor.GitExtractor
import kotlinx.cli.Subcommand
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required

/**
 * CLI command to extract git data to JSON
 */
class ExtractCommand : Subcommand("extract", "Extract git commit data to JSON") {
    private val fromDate by option(
        ArgType.String,
        shortName = "f",
        fullName = "from",
        description = "Start date (e.g., '6 months ago', '2024-01-01')"
    ).default("6 months ago")

    private val toDate by option(
        ArgType.String,
        shortName = "t",
        fullName = "to",
        description = "End date (e.g., 'now', '2024-12-31')"
    ).default("now")

    private val output by option(
        ArgType.String,
        shortName = "o",
        fullName = "output",
        description = "Output JSON file path"
    ).required()

    private val repoName by option(
        ArgType.String,
        fullName = "repo-name",
        description = "Repository name (auto-detected if not provided)"
    )

    private val repoPath by option(
        ArgType.String,
        fullName = "repo-path",
        description = "Path to git repository"
    ).default(".")

    override fun execute() {
        println("Extracting git data...")

        try {
            val expandedPath = ConfigLoader.expandPath(repoPath)

            val extractor = GitExtractor(
                fromDate = fromDate,
                toDate = toDate,
                outputFile = output,
                repoName = repoName,
                repoPath = expandedPath
            )

            val result = extractor.extract()

            println("✓ Extraction successful!")
            println("  Repository: ${result.repository}")
            println("  Commits: ${result.summary.totalCommits}")
            println("  Lines added: ${result.summary.totalLinesAdded}")
            println("  Lines deleted: ${result.summary.totalLinesDeleted}")
            println("  Output: $output")
        } catch (e: Exception) {
            System.err.println("✗ Extraction failed: ${e.message}")
            throw e
        }
    }
}

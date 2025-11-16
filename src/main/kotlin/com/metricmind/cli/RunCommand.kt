package com.metricmind.cli

import com.metricmind.cleaner.RepositoryCleaner
import com.metricmind.config.ConfigLoader
import com.metricmind.database.DatabaseConnection
import com.metricmind.extractor.GitExtractor
import com.metricmind.loader.DataLoader
import com.metricmind.processor.CommitCategorizer
import com.metricmind.processor.WeightCalculator
import kotlinx.cli.Subcommand
import kotlinx.cli.ArgType
import kotlinx.cli.default
import java.io.File

/**
 * CLI command to run complete workflow for multiple repositories
 */
class RunCommand : Subcommand("run", "Run complete extraction and processing workflow") {
    private val configPath by option(
        ArgType.String,
        fullName = "config",
        description = "Path to repositories.json"
    ).default("config/repositories.json")

    private val fromDate by option(
        ArgType.String,
        fullName = "from",
        description = "Start date"
    ).default("6 months ago")

    private val toDate by option(
        ArgType.String,
        fullName = "to",
        description = "End date"
    ).default("now")

    private val clean by option(
        ArgType.Boolean,
        fullName = "clean",
        description = "Clean before processing"
    ).default(false)

    private val singleRepo by option(
        ArgType.String,
        fullName = "repo",
        description = "Process only this repository"
    )

    override fun execute() {
        println("═══════════════════════════════════")
        println("  MetricMind - Git Analytics       ")
        println("═══════════════════════════════════\n")

        try {
            // Initialize configuration and database
            ConfigLoader.init()
            val dbConfig = ConfigLoader.loadDatabaseConfig()
            DatabaseConnection.init(dbConfig)

            // Load repository configuration
            val repoConfig = ConfigLoader.loadRepositoriesConfig(configPath)
            val repos = repoConfig.repositories
                .filter { it.enabled }
                .filter { singleRepo == null || it.name == singleRepo }

            if (repos.isEmpty()) {
                throw IllegalArgumentException(
                    if (singleRepo != null) "Repository not found: $singleRepo"
                    else "No enabled repositories found in $configPath"
                )
            }

            println("Processing ${repos.size} repository(ies)...\n")

            // Process each repository
            repos.forEachIndexed { index, repo ->
                println("[${index + 1}/${repos.size}] Processing: ${repo.name}")
                processRepository(repo.name, repo.path)
                println()
            }

            // Post-processing
            println("Running post-processing...")
            runPostProcessing()

            println("\n✓ Complete workflow finished successfully!")
            println("  Repositories processed: ${repos.size}")
        } catch (e: Exception) {
            System.err.println("\n✗ Workflow failed: ${e.message}")
            throw e
        } finally {
            DatabaseConnection.close()
        }
    }

    /**
     * Process a single repository
     */
    private fun processRepository(name: String, path: String) {
        val expandedPath = ConfigLoader.expandPath(path)
        val outputDir = ConfigLoader.getOutputDir()
        val outputFile = "$outputDir/$name.json"

        // Step 1: Clean (if requested)
        if (clean) {
            println("  → Cleaning existing data...")
            val cleaner = RepositoryCleaner(name, force = true, dryRun = false, deleteRepo = false)
            try {
                cleaner.clean()
            } catch (e: Exception) {
                println("    Warning: Clean failed (repository may not exist yet)")
            }
        }

        // Step 2: Extract
        println("  → Extracting git data...")
        val extractor = GitExtractor(
            fromDate = fromDate,
            toDate = toDate,
            outputFile = outputFile,
            repoName = name,
            repoPath = expandedPath
        )
        val result = extractor.extract()
        println("    ✓ Extracted ${result.summary.totalCommits} commits")

        // Step 3: Load
        println("  → Loading into database...")
        val loader = DataLoader(outputFile)
        val stats = loader.load()
        println("    ✓ Loaded ${stats.commitsInserted} commits (${stats.commitsSkipped} duplicates)")

        // Clean up JSON file
        File(outputFile).delete()
    }

    /**
     * Run post-processing (categorization, weights, views)
     */
    private fun runPostProcessing() {
        // Categorize commits
        println("  → Categorizing commits...")
        val categorizer = CommitCategorizer()
        val catStats = categorizer.categorize()
        println("    ✓ Categorized ${catStats.categorized} commits")

        // Calculate weights
        println("  → Calculating commit weights...")
        val calculator = WeightCalculator()
        val weightStats = calculator.calculate()
        println("    ✓ Processed ${weightStats.revertsFound} reverts, zeroed ${weightStats.commitsZeroed} commits")

        println("  ✓ Post-processing complete")
    }
}

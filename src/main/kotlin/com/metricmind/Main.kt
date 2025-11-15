package com.metricmind

import com.metricmind.cli.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ExperimentalCli
import kotlin.system.exitProcess

/**
 * Main entry point for MetricMind CLI application
 */
@OptIn(ExperimentalCli::class)
fun main(args: Array<String>) {
    val parser = ArgParser("metricmind")

    // Register subcommands
    parser.subcommands(
        ExtractCommand(),
        LoadCommand(),
        CategorizeCommand(),
        WeightCommand(),
        CleanCommand(),
        RunCommand(),
        SetupCommand()
    )

    try {
        parser.parse(args)
    } catch (e: Exception) {
        System.err.println("Error: ${e.message}")
        exitProcess(1)
    }
}

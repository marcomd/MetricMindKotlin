package com.metricmind.models

import kotlinx.serialization.Serializable

/**
 * Represents the complete result of git extraction, serialized to JSON
 */
@Serializable
data class ExtractionResult(
    val repository: String,
    val repositoryPath: String,
    val extractionDate: String,  // ISO 8601 format
    val dateRange: DateRange,
    val summary: Summary,
    val commits: List<Commit>
)

/**
 * Date range for extraction
 */
@Serializable
data class DateRange(
    val from: String,
    val to: String
)

/**
 * Summary statistics for extracted commits
 */
@Serializable
data class Summary(
    val totalCommits: Int,
    val totalLinesAdded: Int,
    val totalLinesDeleted: Int,
    val totalFilesChanged: Int,
    val uniqueAuthors: Int
)

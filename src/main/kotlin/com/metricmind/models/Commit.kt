package com.metricmind.models

import kotlinx.serialization.Serializable

/**
 * Represents a git commit with all its metadata and statistics
 */
@Serializable
data class Commit(
    val hash: String,
    val date: String,  // ISO 8601 format
    val authorName: String,
    val authorEmail: String,
    val subject: String,
    val body: String? = null,
    val linesAdded: Int,
    val linesDeleted: Int,
    val filesChanged: Int,
    val weight: Int = 100,  // 0-100, where 0 = reverted
    val aiTools: String? = null,  // Comma-separated AI tools used
    val category: String? = null,  // Business domain category
    val aiConfidence: Int? = null,  // 0-100, AI categorization confidence (null = not AI-categorized)
    val files: List<CommitFile> = emptyList()
)

/**
 * Represents file-level changes in a commit
 */
@Serializable
data class CommitFile(
    val filename: String,
    val added: Int,
    val deleted: Int
)

/**
 * Represents a business domain category for commit classification
 */
@Serializable
data class Category(
    val id: Int,
    val name: String,
    val description: String? = null,
    val usageCount: Int = 0,
    val createdAt: String,
    val updatedAt: String
)

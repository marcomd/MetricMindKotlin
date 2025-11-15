package com.metricmind.models

import kotlinx.serialization.Serializable

/**
 * Configuration file format for repositories.json
 */
@Serializable
data class RepositoryConfig(
    val repositories: List<RepositoryEntry>
)

/**
 * Individual repository entry in configuration
 */
@Serializable
data class RepositoryEntry(
    val name: String,
    val path: String,
    val description: String? = null,
    val enabled: Boolean = true
)

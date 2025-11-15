package com.metricmind.models

import kotlinx.serialization.Serializable

/**
 * Represents a repository in the system
 */
@Serializable
data class Repository(
    val id: Int? = null,
    val name: String,
    val url: String? = null,
    val description: String? = null,
    val lastExtractedAt: String? = null  // ISO 8601 format
)

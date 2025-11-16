package com.metricmind.database

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * Repositories table definition
 */
object Repositories : IntIdTable("repositories") {
    val name = varchar("name", 255).uniqueIndex()
    val url = text("url").nullable()
    val description = text("description").nullable()
    val lastExtractedAt = timestamp("last_extracted_at").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp())
}

/**
 * Commits table definition
 */
object Commits : IntIdTable("commits") {
    val repositoryId = reference("repository_id", Repositories)
    val hash = varchar("hash", 40)
    val commitDate = timestamp("commit_date")
    val authorName = varchar("author_name", 255)
    val authorEmail = varchar("author_email", 255)
    val subject = text("subject")
    val linesAdded = integer("lines_added").default(0)
    val linesDeleted = integer("lines_deleted").default(0)
    val filesChanged = integer("files_changed").default(0)
    val weight = integer("weight").default(100)
    val aiTools = varchar("ai_tools", 255).nullable()
    val category = varchar("category", 100).nullable()
    val aiConfidence = short("ai_confidence").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())

    init {
        uniqueIndex(repositoryId, hash)
        index(false, repositoryId)
        index(false, commitDate)
        index(false, authorEmail)
        index(false, authorName)
        index(false, hash)
    }
}

/**
 * Categories table definition for approved business domain categories
 */
object Categories : IntIdTable("categories") {
    val name = varchar("name", 100).uniqueIndex()
    val description = text("description").nullable()
    val usageCount = integer("usage_count").default(0)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp())

    init {
        index(false, name)
    }
}

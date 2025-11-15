package com.metricmind.extractor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull

class AiToolsExtractorTest : StringSpec({
    "should extract AI tool from simple format" {
        val body = "**AI tool: Claude Code**"
        val result = AiToolsExtractor.extract(body)
        result shouldBe "CLAUDE CODE"
    }

    "should extract multiple AI tools" {
        val body = "**AI tools: Claude Code and GitHub Copilot**"
        val result = AiToolsExtractor.extract(body)
        result shouldBe "CLAUDE CODE, GITHUB COPILOT"
    }

    "should extract tools without asterisks" {
        val body = "AI tool: Cursor"
        val result = AiToolsExtractor.extract(body)
        result shouldBe "CURSOR"
    }

    "should handle comma-separated tools" {
        val body = "AI tools: Claude Code, GitHub Copilot, Cursor"
        val result = AiToolsExtractor.extract(body)
        result shouldBe "CLAUDE CODE, GITHUB COPILOT, CURSOR"
    }

    "should normalize Copilot variants" {
        val body1 = "AI tool: Copilot"
        AiToolsExtractor.extract(body1) shouldBe "GITHUB COPILOT"

        val body2 = "AI tool: GitHub Copilot"
        AiToolsExtractor.extract(body2) shouldBe "GITHUB COPILOT"
    }

    "should handle case-insensitive matching" {
        val body = "ai tool: claude code"
        val result = AiToolsExtractor.extract(body)
        result shouldBe "CLAUDE CODE"
    }

    "should return null for empty body" {
        AiToolsExtractor.extract(null).shouldBeNull()
        AiToolsExtractor.extract("").shouldBeNull()
        AiToolsExtractor.extract("   ").shouldBeNull()
    }

    "should return null when no AI tools found" {
        val body = "This is a commit message without AI tool info"
        AiToolsExtractor.extract(body).shouldBeNull()
    }

    "should handle ampersand separator" {
        val body = "AI tools: Claude Code & Cursor"
        val result = AiToolsExtractor.extract(body)
        result shouldBe "CLAUDE CODE, CURSOR"
    }

    "should extract from multi-line body" {
        val body = """
            This is a commit message.

            **AI tool: Claude Code**

            Some more text.
        """.trimIndent()
        val result = AiToolsExtractor.extract(body)
        result shouldBe "CLAUDE CODE"
    }

    "should deduplicate tools" {
        val body = "AI tools: Claude Code, Claude Code, Cursor"
        val result = AiToolsExtractor.extract(body)
        result shouldBe "CLAUDE CODE, CURSOR"
    }
})

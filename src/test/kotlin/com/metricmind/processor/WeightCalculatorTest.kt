package com.metricmind.processor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class WeightCalculatorTest : StringSpec({
    "should extract GitLab PR numbers" {
        val subject = "CS | Move HTML content (!10463)"
        val prNumbers = WeightCalculator.extractPrNumbers(subject)
        prNumbers shouldContainExactly listOf("!10463")
    }

    "should extract GitHub PR numbers" {
        val subject = "Fix authentication (#1234)"
        val prNumbers = WeightCalculator.extractPrNumbers(subject)
        prNumbers shouldContainExactly listOf("#1234")
    }

    "should extract multiple PR numbers" {
        val subject = "Merge !123 and #456 together"
        val prNumbers = WeightCalculator.extractPrNumbers(subject)
        prNumbers.size shouldBe 2
        prNumbers shouldContain "!123"
        prNumbers shouldContain "#456"
    }

    "should return empty list when no PR numbers" {
        val subject = "Simple commit without PR"
        val prNumbers = WeightCalculator.extractPrNumbers(subject)
        prNumbers shouldBe emptyList()
    }

    "should detect revert commits" {
        WeightCalculator.isRevertCommit("Revert 'Previous commit'") shouldBe true
        WeightCalculator.isRevertCommit("revert: Fix issue") shouldBe true
        WeightCalculator.isRevertCommit("REVERT previous changes") shouldBe true
    }

    "should not detect unrevert as revert" {
        WeightCalculator.isRevertCommit("Unrevert previous commit") shouldBe false
    }

    "should not detect normal commits as revert" {
        WeightCalculator.isRevertCommit("Fix authentication") shouldBe false
        WeightCalculator.isRevertCommit("Add new feature") shouldBe false
    }

    "should detect unrevert commits" {
        WeightCalculator.isUnrevertCommit("Unrevert !10463 and fix error") shouldBe true
        WeightCalculator.isUnrevertCommit("unrevert: Previous commit") shouldBe true
        WeightCalculator.isUnrevertCommit("UNREVERT changes") shouldBe true
    }

    "should not detect normal commits as unrevert" {
        WeightCalculator.isUnrevertCommit("Fix issue") shouldBe false
        WeightCalculator.isUnrevertCommit("Revert commit") shouldBe false
    }

    "should handle real-world revert example" {
        val subject = "Revert 'CS | Move HTML content (!10463)'"
        WeightCalculator.isRevertCommit(subject) shouldBe true
        val prNumbers = WeightCalculator.extractPrNumbers(subject)
        prNumbers shouldContainExactly listOf("!10463")
    }

    "should handle real-world unrevert example" {
        val subject = "Unrevert !10463 and fix error (!10660)"
        WeightCalculator.isUnrevertCommit(subject) shouldBe true
        val prNumbers = WeightCalculator.extractPrNumbers(subject)
        prNumbers.size shouldBe 2
        prNumbers shouldContain "!10463"
        prNumbers shouldContain "!10660"
    }

    "should handle PR numbers without parentheses" {
        val subject = "Fix !123 and #456"
        val prNumbers = WeightCalculator.extractPrNumbers(subject)
        prNumbers.size shouldBe 2  // Matches with or without parentheses
        prNumbers shouldContain "!123"
        prNumbers shouldContain "#456"
    }

    "should extract PR numbers in parentheses only" {
        val subject = "Merge (!123) and (!456)"
        val prNumbers = WeightCalculator.extractPrNumbers(subject)
        prNumbers.size shouldBe 2
        prNumbers shouldContain "!123"
        prNumbers shouldContain "!456"
    }
})

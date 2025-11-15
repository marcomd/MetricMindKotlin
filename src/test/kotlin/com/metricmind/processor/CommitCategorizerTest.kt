package com.metricmind.processor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull

class CommitCategorizerTest : StringSpec({
    "should extract category from pipe delimiter" {
        val subject = "BILLING | Implement payment gateway"
        CommitCategorizer.extractCategory(subject) shouldBe "BILLING"
    }

    "should extract category from square brackets" {
        val subject = "[CS] Fix customer widget"
        CommitCategorizer.extractCategory(subject) shouldBe "CS"
    }

    "should extract category from first uppercase word" {
        val subject = "BILLING Implement payment"
        CommitCategorizer.extractCategory(subject) shouldBe "BILLING"
    }

    "should exclude common commit verbs" {
        CommitCategorizer.extractCategory("MERGE branch feature").shouldBeNull()
        CommitCategorizer.extractCategory("FIX authentication bug").shouldBeNull()
        CommitCategorizer.extractCategory("ADD new feature").shouldBeNull()
        CommitCategorizer.extractCategory("UPDATE dependencies").shouldBeNull()
        CommitCategorizer.extractCategory("REMOVE old code").shouldBeNull()
        CommitCategorizer.extractCategory("DELETE unused files").shouldBeNull()
    }

    "should return null for lowercase first word" {
        val subject = "fix authentication bug"
        CommitCategorizer.extractCategory(subject).shouldBeNull()
    }

    "should return null for empty subject" {
        CommitCategorizer.extractCategory(null).shouldBeNull()
        CommitCategorizer.extractCategory("").shouldBeNull()
        CommitCategorizer.extractCategory("   ").shouldBeNull()
    }

    "should handle multiple pipes and use first part" {
        val subject = "BILLING | CS | Fix issue"
        CommitCategorizer.extractCategory(subject) shouldBe "BILLING"
    }

    "should handle square brackets with spaces" {
        val subject = "[ CS ] Fix customer widget"
        CommitCategorizer.extractCategory(subject) shouldBe "CS"
    }

    "should prioritize pipe over square brackets" {
        val subject = "BILLING | [CS] Description"
        CommitCategorizer.extractCategory(subject) shouldBe "BILLING"
    }

    "should prioritize pipe over first word" {
        val subject = "INFRA | BILLING Description"
        CommitCategorizer.extractCategory(subject) shouldBe "INFRA"
    }

    "should prioritize square brackets over first word" {
        val subject = "[CS] BILLING Description"
        CommitCategorizer.extractCategory(subject) shouldBe "CS"
    }

    "should handle real-world examples" {
        CommitCategorizer.extractCategory("CS | Move HTML content (!10463)") shouldBe "CS"
        CommitCategorizer.extractCategory("[INFRA] Update database schema") shouldBe "INFRA"
        CommitCategorizer.extractCategory("BILLING Implement new payment flow") shouldBe "BILLING"
        CommitCategorizer.extractCategory("Fix bug in authentication").shouldBeNull()
    }

    "should normalize to uppercase" {
        CommitCategorizer.extractCategory("billing | Description") shouldBe "BILLING"
        CommitCategorizer.extractCategory("[cs] Description") shouldBe "CS"
    }

    "should handle single letter categories" {
        CommitCategorizer.extractCategory("A | Description").shouldBeNull() // Too short
        CommitCategorizer.extractCategory("AB | Description") shouldBe "AB"
        CommitCategorizer.extractCategory("[A] Description").shouldBeNull() // Too short
    }
})

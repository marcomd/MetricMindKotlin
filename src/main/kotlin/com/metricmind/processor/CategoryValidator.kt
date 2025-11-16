package com.metricmind.processor

/**
 * Validator for category names to ensure they meet business requirements
 *
 * Categories must be:
 * - Business-focused (e.g., BILLING, CS, INFRA)
 * - Not version numbers (e.g., "2.58.0")
 * - Not issue numbers (e.g., "#6802")
 * - Not purely numeric (e.g., "2023")
 * - Start with a letter (not "#" or digit)
 * - Reasonable length (2-50 characters)
 * - Contain at least one letter
 */
object CategoryValidator {
    private const val MIN_LENGTH = 2
    private const val MAX_LENGTH = 50
    private const val MAX_DIGIT_RATIO = 0.5

    // Regex patterns for validation
    private val PURELY_NUMERIC = Regex("""^\d+$""")
    private val VERSION_NUMBER = Regex("""^\d+\.\d+""")
    private val ISSUE_NUMBER = Regex("""^#\d+$""")
    private val STARTS_WITH_HASH = Regex("""^#""")
    private val CONTAINS_LETTER = Regex("""[A-Za-z]""")

    /**
     * Validate a category name
     *
     * @param category The category name to validate
     * @param preventNumeric Whether to enforce numeric category prevention (from config)
     * @return true if valid, false otherwise
     */
    fun isValid(category: String?, preventNumeric: Boolean = true): Boolean {
        // Basic checks
        if (category.isNullOrBlank()) {
            return false
        }

        // Length checks
        if (category.length < MIN_LENGTH || category.length > MAX_LENGTH) {
            return false
        }

        // Must contain at least one letter
        if (!CONTAINS_LETTER.containsMatchIn(category)) {
            return false
        }

        // Numeric prevention checks (if enabled)
        if (preventNumeric) {
            // Reject purely numeric (e.g., "2023")
            if (PURELY_NUMERIC.matches(category)) {
                return false
            }

            // Reject version numbers (e.g., "2.58.0")
            if (VERSION_NUMBER.containsMatchIn(category)) {
                return false
            }

            // Reject issue numbers (e.g., "#6802")
            if (ISSUE_NUMBER.matches(category)) {
                return false
            }

            // Reject if starts with #
            if (STARTS_WITH_HASH.containsMatchIn(category)) {
                return false
            }

            // Reject if >50% digits
            val digitCount = category.count { it.isDigit() }
            val digitRatio = digitCount.toDouble() / category.length
            if (digitRatio > MAX_DIGIT_RATIO) {
                return false
            }
        }

        return true
    }

    /**
     * Get detailed rejection reason for a category
     *
     * @param category The category name that failed validation
     * @param preventNumeric Whether numeric prevention is enabled
     * @return Human-readable rejection reason
     */
    fun rejectionReason(category: String?, preventNumeric: Boolean = true): String {
        if (category == null) {
            return "category is null"
        }

        if (category.isBlank()) {
            return "category is blank"
        }

        if (category.length < MIN_LENGTH) {
            return "too short (minimum $MIN_LENGTH characters)"
        }

        if (category.length > MAX_LENGTH) {
            return "too long (maximum $MAX_LENGTH characters)"
        }

        if (!CONTAINS_LETTER.containsMatchIn(category)) {
            return "must contain at least one letter"
        }

        if (preventNumeric) {
            if (PURELY_NUMERIC.matches(category)) {
                return "cannot be purely numeric (e.g., '2023')"
            }

            if (VERSION_NUMBER.containsMatchIn(category)) {
                return "cannot be a version number (e.g., '2.58.0')"
            }

            if (ISSUE_NUMBER.matches(category)) {
                return "cannot be an issue number (e.g., '#6802')"
            }

            if (STARTS_WITH_HASH.containsMatchIn(category)) {
                return "cannot start with '#'"
            }

            val digitCount = category.count { it.isDigit() }
            val digitRatio = digitCount.toDouble() / category.length
            if (digitRatio > MAX_DIGIT_RATIO) {
                return "too many digits (>${(MAX_DIGIT_RATIO * 100).toInt()}% of characters)"
            }
        }

        return "unknown reason"
    }

    /**
     * Validate and throw exception if invalid
     *
     * @param category The category name to validate
     * @param preventNumeric Whether numeric prevention is enabled
     * @throws IllegalArgumentException if category is invalid
     */
    fun validateOrThrow(category: String?, preventNumeric: Boolean = true) {
        if (!isValid(category, preventNumeric)) {
            val reason = rejectionReason(category, preventNumeric)
            throw IllegalArgumentException("Invalid category '$category': $reason")
        }
    }
}

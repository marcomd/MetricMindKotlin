package com.metricmind.extractor

/**
 * Extracts AI tools information from commit message bodies
 */
object AiToolsExtractor {
    // Pattern to match "AI tool:" or "AI tools:" (case-insensitive)
    private val aiToolsPattern = Regex(
        """\*{0,2}\s*AI\s+tools?\s*:\s*([^\n*]+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Extract AI tools from commit body
     * @param body The commit message body
     * @return Comma-separated normalized AI tool names, or null if not found
     *
     * Example inputs:
     * - "**AI tool: Claude Code**" → "CLAUDE CODE"
     * - "**AI tools: Claude Code and GitHub Copilot**" → "CLAUDE CODE, GITHUB COPILOT"
     * - "AI tool: Cursor" → "CURSOR"
     */
    fun extract(body: String?): String? {
        if (body.isNullOrBlank()) return null

        val match = aiToolsPattern.find(body) ?: return null
        val toolsString = match.groupValues[1].trim()

        // Split by common delimiters: "and", "&", ","
        val splitRegex = Regex("""\s+and\s+|\s*&\s*|\s*,\s*""", RegexOption.IGNORE_CASE)
        val tools = splitRegex.split(toolsString)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { normalizeTool(it) }
            .distinct()

        return if (tools.isEmpty()) null else tools.joinToString(", ")
    }

    /**
     * Normalize tool names to uppercase standard names
     */
    private fun normalizeTool(tool: String): String {
        val normalized = tool.trim().uppercase()

        return when {
            // Claude variations
            normalized.contains("CLAUDE CODE") -> "CLAUDE CODE"
            normalized == "CLAUDE" -> "CLAUDE"

            // Cursor
            normalized.contains("CURSOR") -> "CURSOR"

            // GitHub Copilot variations
            normalized.contains("GITHUB COPILOT") -> "GITHUB COPILOT"
            normalized.contains("COPILOT") -> "GITHUB COPILOT"

            // ChatGPT
            normalized.contains("CHATGPT") || normalized.contains("CHAT GPT") -> "CHATGPT"

            // Codeium
            normalized.contains("CODEIUM") -> "CODEIUM"

            // Tabnine
            normalized.contains("TABNINE") -> "TABNINE"

            // Default: return as-is (uppercase)
            else -> normalized
        }
    }
}

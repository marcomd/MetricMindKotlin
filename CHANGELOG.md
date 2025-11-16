# Changelog

All notable changes to MetricMind Kotlin will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2025-11-16

### Added
- **AI-powered categorization** with support for Gemini and Ollama LLM providers
  - Intelligent commit categorization using large language models
  - Automatic category creation with confidence scoring (0-100)
  - Fallback to pattern-based categorization when AI is disabled
  - Configuration via environment variables (AI_PROVIDER, GEMINI_API_KEY, OLLAMA_URL, etc.)

- **Category validation system** to prevent invalid categories
  - Rejects numeric values (e.g., "2023", "#6802")
  - Rejects version numbers (e.g., "2.58.0")
  - Enforces business-focused category names
  - Configurable via PREVENT_NUMERIC_CATEGORIES flag

- **Categories database table** for tracking approved categories
  - Stores category name, description, and usage count
  - Automatically populated from existing commits
  - Supports upsert operations for new categories

- **AI confidence tracking** in commits table
  - `ai_confidence` column (0-100) tracks LLM confidence scores
  - Helps identify low-confidence categorizations for review
  - NULL for pattern-based categorizations

- **Repository description field** throughout data pipeline
  - JSON extraction results include `repository_description`
  - Database `repositories` table populated with descriptions
  - Configurable in repositories.json configuration file

- **Comprehensive AI configuration**
  - Support for both Gemini (cloud) and Ollama (local) providers
  - Configurable timeout, retries, and temperature
  - Retry logic with exponential backoff (2s, 4s, 8s)
  - Detailed error handling and logging

- **VERSION file** for single source of truth versioning

### Changed
- **Weight calculation refactored** to prevent double-counting
  - Removed `processUnreverts()` method that incorrectly restored weights
  - Removed second pass that processed unrevert commits
  - Simplified to single-pass algorithm: only processes revert commits
  - **Correct workflow**: Original (100) → Revert (both to 0) → Unrevert (stays 100, original stays 0)
  - Prevents work from being counted twice

- **Updated statistics** to remove misleading "Commits restored" metric
- **Enhanced database schema** with V6 migration for AI features

### Fixed
- **Double-counting bug** in weight calculation
  - Original commits no longer incorrectly restored to weight=100 by unrevert commits
  - Each unit of work now counted exactly once

### Dependencies
- Added Ktor HTTP client (v2.3.7) for AI API integration
  - `ktor-client-core`, `ktor-client-cio`
  - `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json`
  - `ktor-client-logging`

### Notes
- AI categorization is **opt-in** - set `AI_PROVIDER` to enable
- Existing pattern-based categorization remains available as fallback
- Database migrations are backward-compatible
- No breaking changes to existing CLI commands

---

## [0.3.0] - 2025-01-15

### Added
- Weight calculation for tracking reverted commits
- AI tools extraction from commit messages
- Database views for analytics (daily, weekly, monthly, category)
- Materialized views for performance optimization

### Changed
- Enhanced database schema with weight and ai_tools columns

---

## [0.2.0] - 2025-01-10

### Added
- Pattern-based commit categorization
  - Pipe delimiter: `CATEGORY | Subject`
  - Square brackets: `[CATEGORY] Subject`
  - First uppercase word (excluding common verbs)
- Category column in commits table

---

## [0.1.0] - 2025-01-05

### Added
- Initial Kotlin port of MetricMind Ruby project
- Git data extraction with commit statistics
- PostgreSQL database integration using Exposed ORM
- HikariCP connection pooling
- Flyway database migrations
- Multi-repository support via repositories.json
- CLI commands: extract, load, categorize, weight, clean, run, setup
- Comprehensive test suite with Kotest and Testcontainers
- Docker support for development
- GitHub Actions CI/CD pipeline

---

## Version History

- **1.0.0** (2025-11-16) - AI categorization, weight calculation fix, description field
- **0.3.0** (2025-01-15) - Weight calculation, AI tools tracking, analytics views
- **0.2.0** (2025-01-10) - Pattern-based categorization
- **0.1.0** (2025-01-05) - Initial release

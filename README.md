# MetricMind - Git Productivity Analytics (Kotlin)

An AI-driven developer productivity analytics system that extracts, stores, and visualizes git commit data from multiple repositories to measure the impact of development tools and practices.

**This is the Kotlin implementation** - a complete rewrite from Ruby/Bash to Kotlin with modern JVM tooling.

## Features

- **Git Data Extraction**: Parse commit history with file-level statistics
- **AI Tools Tracking**: Automatically detect AI tools used (Claude Code, Cursor, GitHub Copilot, etc.)
- **Business Domain Categorization**: Extract categories from commit messages (BILLING, CS, INFRA, etc.)
- **Revert Detection**: Calculate commit weights based on revert/unrevert patterns
- **PostgreSQL Storage**: Efficient storage with indexes and materialized views
- **Multi-Repository Support**: Process multiple repositories in one workflow
- **Comprehensive Analytics**: Pre-computed views for fast dashboard queries

## Quick Start

### Prerequisites

- **Java 21+** ([Download](https://adoptium.net/))
- **PostgreSQL 12+** ([Download](https://www.postgresql.org/download/))
- **Git** (for extracting repository data)

### Installation

```bash
# Clone the repository
cd MetricMindKotlin

# Build the project
./gradlew build

# Or build the Fat JAR
./gradlew shadowJar
```

### Setup

```bash
# 1. Set up database and run migrations
./gradlew run --args="setup"

# 2. Configure environment (copy and edit)
cp .env.example .env
# Edit .env with your PostgreSQL credentials

# 3. Configure repositories (copy and edit)
cp config/repositories.example.json config/repositories.json
# Edit config/repositories.json with your repository paths

# 4. Run complete workflow
./gradlew run --args="run"
```

That's it! The system will extract, load, categorize, and calculate weights automatically.

## Usage

### Command Line Interface

MetricMind provides a comprehensive CLI with multiple commands:

#### Setup Database

```bash
# Initialize database schema and migrations
./gradlew run --args="setup"
```

#### Extract Git Data

```bash
# Extract from current directory
./gradlew run --args="extract --output data/my-repo.json"

# Extract with date range
./gradlew run --args="extract --from '1 year ago' --to 'now' --output data/repo.json"

# Extract from specific path
./gradlew run --args="extract --repo-path /path/to/repo --output data/repo.json"
```

#### Load Data to Database

```bash
./gradlew run --args="load data/my-repo.json"
```

#### Categorize Commits

```bash
# Categorize all commits
./gradlew run --args="categorize"

# Categorize specific repository
./gradlew run --args="categorize --repo my-backend"

# Dry run (preview without saving)
./gradlew run --args="categorize --dry-run"
```

#### Calculate Commit Weights

```bash
# Calculate weights for all commits
./gradlew run --args="weights"

# Calculate for specific repository
./gradlew run --args="weights --repo my-backend"

# Dry run
./gradlew run --args="weights --dry-run"
```

#### Clean Repository Data

```bash
# Clean commits (keeps repository record)
./gradlew run --args="clean my-backend"

# Clean commits and repository record
./gradlew run --args="clean my-backend --delete-repo"

# Skip confirmation
./gradlew run --args="clean my-backend --force"
```

#### Run Complete Workflow

```bash
# Process all enabled repositories
./gradlew run --args="run"

# Process single repository
./gradlew run --args="run my-backend"

# Custom date range
./gradlew run --args="run --from '6 months ago' --to 'now'"

# Clean before processing
./gradlew run --args="run --clean"
```

### Using the Fat JAR

After building the Fat JAR, you can run it directly:

```bash
# Build Fat JAR
./gradlew shadowJar

# Run commands
java -jar build/libs/metricmind-all-1.0.0.jar setup
java -jar build/libs/metricmind-all-1.0.0.jar run
java -jar build/libs/metricmind-all-1.0.0.jar extract --output data/repo.json
```

Create a wrapper script for convenience:

```bash
#!/bin/bash
# metricmind.sh
java -jar build/libs/metricmind-all-1.0.0.jar "$@"
```

```bash
chmod +x metricmind.sh
./metricmind.sh run
```

## Configuration

### Environment Variables (.env)

```bash
# Option 1: DATABASE_URL (recommended for hosted databases)
DATABASE_URL=postgresql://user:password@host:port/database?sslmode=require

# Option 2: Individual parameters (local development)
PGHOST=localhost
PGPORT=5432
PGDATABASE=git_analytics
PGUSER=your_username
PGPASSWORD=your_password

# Optional settings
DEFAULT_FROM_DATE="6 months ago"
DEFAULT_TO_DATE="now"
OUTPUT_DIR=./data/exports
```

### Repository Configuration (config/repositories.json)

```json
{
  "repositories": [
    {
      "name": "my-backend",
      "path": "/absolute/path/to/repo",
      "description": "Backend API",
      "enabled": true
    },
    {
      "name": "my-frontend",
      "path": "~/path/to/frontend",
      "description": "React Frontend",
      "enabled": true
    }
  ]
}
```

## Architecture

### Technology Stack

- **Language**: Kotlin 2.2.21
- **Java**: Java 21+
- **Build Tool**: Gradle (Kotlin DSL)
- **Database ORM**: Exposed (JetBrains)
- **Database**: PostgreSQL 12+
- **Migrations**: Flyway
- **Connection Pooling**: HikariCP
- **CLI**: kotlinx-cli
- **JSON**: kotlinx-serialization
- **Logging**: kotlin-logging + Logback
- **Testing**: Kotest + MockK + Testcontainers

### Project Structure

```
src/
├── main/
│   ├── kotlin/com/metricmind/
│   │   ├── cli/              # CLI commands
│   │   ├── database/         # Database layer (Exposed, connection)
│   │   ├── extractor/        # Git extraction logic
│   │   ├── loader/           # Data loading to database
│   │   ├── processor/        # Categorization & weight calculation
│   │   ├── cleaner/          # Repository cleanup
│   │   ├── config/           # Configuration loading
│   │   ├── models/           # Data models
│   │   └── Main.kt           # Entry point
│   └── resources/
│       ├── db/migration/     # Flyway migrations
│       └── logback.xml       # Logging configuration
└── test/
    └── kotlin/com/metricmind/
        ├── extractor/        # Extractor tests
        └── processor/        # Processor tests
```

### Database Schema

The application uses Flyway migrations to manage the database schema:

- **V1**: Initial schema (repositories, commits tables)
- **V2**: Add categorization (category column)
- **V3**: Add weight and AI tools tracking
- **V4**: Create standard analytics views
- **V5**: Create category analytics views

All views include both weighted and unweighted metrics.

## Testing

### Run All Tests

```bash
./gradlew test
```

### Run Specific Test

```bash
./gradlew test --tests "com.metricmind.extractor.AiToolsExtractorTest"
```

### Test Coverage

```bash
./gradlew test jacocoTestReport
# Report: build/reports/jacoco/test/html/index.html
```

### Test Structure

- **Unit Tests**: Business logic (AiToolsExtractor, CommitCategorizer, WeightCalculator)
- **Integration Tests**: Database operations with Testcontainers
- **E2E Tests**: Complete workflows

## Development

### Build

```bash
# Compile
./gradlew compileKotlin

# Build (compile + test)
./gradlew build

# Build without tests
./gradlew build -x test

# Build Fat JAR
./gradlew shadowJar
```

### Run from Source

```bash
./gradlew run --args="<command> [options]"
```

### Code Quality

```bash
# Format code (if ktlint is configured)
./gradlew ktlintFormat

# Check code style
./gradlew ktlintCheck
```

## AI Tools Tracking

Automatically detect AI tools from commit messages:

### Commit Message Format

```
Your commit subject

**AI tool: Claude Code**

Rest of commit body...
```

### Supported Tools

- Claude Code
- Claude
- Cursor
- GitHub Copilot / Copilot
- ChatGPT
- Codeium
- Tabnine

### Query AI Tools Data

```sql
-- AI tools usage statistics
SELECT * FROM v_ai_tools_stats ORDER BY total_commits DESC;

-- Compare productivity with/without AI tools
SELECT
  CASE WHEN ai_tools IS NOT NULL THEN 'With AI' ELSE 'Without AI' END,
  COUNT(*) as commits,
  AVG(lines_added + lines_deleted) as avg_lines
FROM commits
WHERE weight > 0
GROUP BY (CASE WHEN ai_tools IS NOT NULL THEN 'With AI' ELSE 'Without AI' END);
```

## Commit Categorization

Extract business domains from commit subjects:

### Patterns

1. **Pipe delimiter**: `BILLING | Description` → BILLING
2. **Square brackets**: `[CS] Description` → CS
3. **First uppercase word**: `BILLING Description` → BILLING

### Query Categories

```sql
-- Category distribution
SELECT * FROM v_category_stats ORDER BY total_commits DESC;

-- Category by repository
SELECT * FROM v_category_by_repo WHERE repository_name = 'MyApp';

-- Monthly category trends
SELECT * FROM mv_monthly_category_stats
WHERE repository_name = 'MyApp'
ORDER BY month_start_date DESC;
```

## Weight Calculation (Revert Detection)

Tracks commit validity by detecting reverted commits:

### How It Works

```
1. CS | Move HTML content (!10463)           → weight = 100
2. Revert "CS | Move HTML content (!10463)"  → weight = 0 (also sets #1 to 0)
3. Unrevert !10463 and fix error (!10660)    → weight = 100 (restores #1)
```

### Query Weighted Metrics

```sql
-- Compare weighted vs unweighted metrics
SELECT
  repository_name,
  year_month,
  total_lines_changed,      -- All commits
  weighted_lines_changed    -- Excluding reverted
FROM mv_monthly_stats_by_repo
ORDER BY year_month DESC;

-- Find reverted commits
SELECT * FROM v_reverted_commits LIMIT 20;
```

## Troubleshooting

### Build Issues

```bash
# Clean build
./gradlew clean build

# Refresh dependencies
./gradlew build --refresh-dependencies
```

### Database Connection

```bash
# Test connection
psql -h $PGHOST -U $PGUSER -d $PGDATABASE -c "SELECT version();"

# Check database exists
psql -l | grep git_analytics
```

### Gradle Wrapper Not Found

```bash
# Download Gradle wrapper
gradle wrapper --gradle-version=8.5
```

## Comparison: Ruby vs Kotlin

| Feature | Ruby Version | Kotlin Version |
|---------|-------------|----------------|
| Language | Ruby 3.3+ | Kotlin 1.9.21 |
| Database | pg gem | Exposed ORM + JDBC |
| CLI | Custom scripts | kotlinx-cli |
| Orchestration | Bash scripts | Kotlin RunCommand |
| Testing | RSpec | Kotest |
| Packaging | Ruby interpreter required | Fat JAR (standalone) |
| Performance | Moderate | Faster (JVM) |
| Type Safety | Dynamic | Static typing |

## Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

## License

Internal use only.

## Support

For issues or questions:
- Check the [Troubleshooting](#troubleshooting) section
- Review logs in `logs/metricmind.log`
- Contact the development team

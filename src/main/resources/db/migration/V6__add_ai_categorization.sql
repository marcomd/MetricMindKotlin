-- Migration V6: Add AI Categorization Support
-- This migration adds:
-- 1. Categories table for approved business domain categories
-- 2. AI confidence tracking for commits
-- 3. Indexes for efficient queries
-- 4. Constraints for data validation

-- Create categories table
CREATE TABLE IF NOT EXISTS categories (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    usage_count INTEGER DEFAULT 0 NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index for category lookups
CREATE INDEX IF NOT EXISTS idx_categories_name ON categories(name);

-- Add ai_confidence column to commits table (0-100, NULL if not AI-categorized)
ALTER TABLE commits
ADD COLUMN IF NOT EXISTS ai_confidence SMALLINT;

-- Add constraint: ai_confidence between 0 and 100 or NULL
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'check_ai_confidence_range'
    ) THEN
        ALTER TABLE commits
        ADD CONSTRAINT check_ai_confidence_range
        CHECK (ai_confidence IS NULL OR (ai_confidence >= 0 AND ai_confidence <= 100));
    END IF;
END$$;

-- Index for low-confidence queries (partial index)
CREATE INDEX IF NOT EXISTS idx_commits_ai_confidence
ON commits(ai_confidence)
WHERE ai_confidence IS NOT NULL;

-- Comments for documentation
COMMENT ON TABLE categories IS 'Approved business domain categories for commit categorization';
COMMENT ON COLUMN categories.name IS 'Unique category name (UPPERCASE, 1-2 words, e.g., BILLING, CS, INFRA)';
COMMENT ON COLUMN categories.description IS 'Description of what this category represents';
COMMENT ON COLUMN categories.usage_count IS 'Number of commits assigned to this category';
COMMENT ON COLUMN commits.ai_confidence IS 'AI categorization confidence score (0-100). NULL if not AI-categorized or pattern-based.';

-- Seed initial categories from existing commits
INSERT INTO categories (name, description, usage_count)
SELECT
    DISTINCT category as name,
    'Extracted from existing commits' as description,
    COUNT(*) as usage_count
FROM commits
WHERE category IS NOT NULL
GROUP BY category
ON CONFLICT (name) DO NOTHING;

-- Add commit categorization
-- Adds category column to commits table for business domain tracking

-- Add category column to commits table
ALTER TABLE commits
ADD COLUMN category VARCHAR(100);

-- Create index for efficient category queries
CREATE INDEX idx_commits_category ON commits(category) WHERE category IS NOT NULL;

-- Add comment for documentation
COMMENT ON COLUMN commits.category IS 'Business domain category extracted from commit subject (e.g., BILLING, CS)';

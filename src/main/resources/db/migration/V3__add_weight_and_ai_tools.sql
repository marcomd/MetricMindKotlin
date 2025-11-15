-- Add weight and AI tools tracking
-- Adds weight (0-100 for commit validity) and ai_tools columns to commits table

-- Add weight column to commits table (0-100, default 100)
ALTER TABLE commits
ADD COLUMN weight INTEGER DEFAULT 100 NOT NULL;

-- Add constraint to ensure weight is between 0 and 100
ALTER TABLE commits
ADD CONSTRAINT check_weight_range CHECK (weight >= 0 AND weight <= 100);

-- Add ai_tools column to commits table
ALTER TABLE commits
ADD COLUMN ai_tools VARCHAR(255);

-- Create index for efficient ai_tools queries
CREATE INDEX idx_commits_ai_tools ON commits(ai_tools) WHERE ai_tools IS NOT NULL;

-- Add comments for documentation
COMMENT ON COLUMN commits.weight IS 'Commit validity weight (0-100). Reverted commits have weight=0, valid commits have weight=100.';
COMMENT ON COLUMN commits.ai_tools IS 'AI tools used during development, extracted from commit body (e.g., CLAUDE CODE, CURSOR, GITHUB COPILOT).';

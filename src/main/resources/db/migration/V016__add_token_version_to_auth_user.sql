-- Add token_version column to auth_user for JWT token invalidation support
-- This allows immediate invalidation of all user tokens without changing password

ALTER TABLE auth_user 
ADD COLUMN token_version BIGINT DEFAULT 0 NOT NULL;

-- Add index for faster token version lookups during JWT validation
CREATE INDEX idx_auth_user_token_version ON auth_user(id, token_version);

-- Comment explaining the purpose
COMMENT ON COLUMN auth_user.token_version IS 
'Token version for JWT invalidation. Increment to invalidate all existing tokens for this user.';

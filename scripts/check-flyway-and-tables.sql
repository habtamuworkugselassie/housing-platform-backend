-- Run this on your PROD database to see why "new" tables might be missing.
-- Connect with: psql $DATABASE_URL -f scripts/check-flyway-and-tables.sql

\echo '=== 1. Flyway schema history (last 15) ==='
SELECT installed_rank, version, description, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 15;

\echo ''
\echo '=== 2. Tables that newer migrations create (do they exist?) ==='
SELECT table_schema, table_name
FROM information_schema.tables
WHERE table_name IN (
  'exhibition_interest',
  'media_attachments',
  'password_reset_tokens',
  'project_tasks',
  'project_team_members',
  'project_milestones',
  'project_documents',
  'project_issues'
)
ORDER BY table_schema, table_name;

\echo ''
\echo '=== 3. If exhibition_interest exists, show columns ==='
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_name = 'exhibition_interest'
ORDER BY ordinal_position;

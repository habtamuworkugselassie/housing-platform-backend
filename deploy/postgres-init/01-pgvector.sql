-- Runs once, when the postgres container initialises an EMPTY data volume
-- (docker-entrypoint-initdb.d). V46__support_rag_pgvector.sql needs the vector
-- type but does not create the extension itself, because on the old managed
-- database the Flyway user was not a superuser. Here POSTGRES_USER owns the
-- instance, so the extension is enabled before the backend ever connects.
--
-- For a volume that already exists this file is never read; the deploy workflow
-- runs the same statement on every deploy, which is a no-op once it is in place.
CREATE EXTENSION IF NOT EXISTS vector;

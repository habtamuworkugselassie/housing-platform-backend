-- Migration: Add audit columns to material_usage table
-- This migration adds created_by and updated_by columns that are required by BaseAuditEntity

ALTER TABLE material_usage
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(255),
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);

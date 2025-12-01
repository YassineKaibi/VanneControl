-- Migration: Add user profile fields to users table
-- Date: 2025-12-02
-- Description: Adds first_name, last_name, phone_number, date_of_birth, location, avatar_url, and preferences

-- Add new columns to users table
ALTER TABLE users
ADD COLUMN IF NOT EXISTS first_name TEXT,
ADD COLUMN IF NOT EXISTS last_name TEXT,
ADD COLUMN IF NOT EXISTS phone_number TEXT,
ADD COLUMN IF NOT EXISTS date_of_birth DATE,
ADD COLUMN IF NOT EXISTS location TEXT,
ADD COLUMN IF NOT EXISTS avatar_url TEXT,
ADD COLUMN IF NOT EXISTS preferences JSONB DEFAULT '{}'::JSONB;

-- Update existing users to have default empty preferences if null
UPDATE users SET preferences = '{}'::JSONB WHERE preferences IS NULL;

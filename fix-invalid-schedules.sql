-- SQL script to identify and fix schedules that will never fire
--
-- Usage:
--   docker compose exec -T postgres psql -U piston_user -d piston_control < fix-invalid-schedules.sql
--
-- This script helps identify schedules with cron expressions that represent times in the past
-- or will otherwise never fire.

-- Step 1: View all enabled schedules to identify problematic ones
SELECT
    id,
    name,
    cron_expression,
    device_id,
    piston_number,
    action,
    enabled,
    created_at
FROM schedules
WHERE enabled = true
ORDER BY created_at DESC;

-- Step 2: Disable schedules that are likely problematic
-- (You should manually review the schedules above and update the WHERE clause with the specific IDs)
--
-- Example: Disable a specific problematic schedule
-- UPDATE schedules
-- SET enabled = false, updated_at = NOW()
-- WHERE id = '79dd5f18-204a-4feb-b421-1de2d7a495c8';

-- Step 3: Or bulk disable all schedules and re-enable them one by one after fixing:
-- UPDATE schedules SET enabled = false WHERE enabled = true;

-- Step 4: Common fixes for cron expressions that won't fire:
--
-- Daily at 9 AM:          0 0 9 * * ?
-- Every hour:             0 0 * * * ?
-- Every 30 minutes:       0 */30 * * * ?
-- Mon-Fri at 8 AM:        0 0 8 ? * MON-FRI
-- First day of month:     0 0 0 1 * ?
-- Every Sunday at noon:   0 0 12 ? * SUN
--
-- Example update:
-- UPDATE schedules
-- SET cron_expression = '0 0 9 * * ?', updated_at = NOW()
-- WHERE id = '79dd5f18-204a-4feb-b421-1de2d7a495c8';

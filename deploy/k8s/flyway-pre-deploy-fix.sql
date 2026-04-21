-- =============================================================================
-- Flyway Pre-Deploy Fix for 20260421 Release
-- =============================================================================
-- PROBLEM: The upstream merge renumbered migrations V39-V42 → V40-V43 to make
-- room for the new upstream V39 (password_reset_request). Production DB still
-- has the old numbering. Running the new code directly would cause a checksum
-- mismatch on V39 (content changed from security_audit → password_reset_request).
--
-- SOLUTION: Renumber existing rows in flyway_schema_history from V39-V42 to
-- V40-V43 (reverse order to avoid PK conflicts), then fix checksums so Flyway
-- recognizes V40-V43 as already applied. V39 (password_reset_request) will be
-- applied automatically as a new migration on first startup.
--
-- USAGE: Run this script on the production database BEFORE deploying the new
-- server image (20260421).
--
--   kubectl -n skillhub exec -it deploy/skillhub-postgres -- \
--     psql -U skillhub -d skillhub -f /tmp/flyway-pre-deploy-fix.sql
--
-- Or copy into the pod first:
--   kubectl -n skillhub cp flyway-pre-deploy-fix.sql skillhub-postgres-xxx:/tmp/
-- =============================================================================

BEGIN;

-- Step 1: Verify current state (should show V39-V42 with security_audit chain)
DO $$
DECLARE
    v39_desc TEXT;
BEGIN
    SELECT description INTO v39_desc
    FROM flyway_schema_history
    WHERE version = '39';

    IF v39_desc IS NULL THEN
        RAISE EXCEPTION 'V39 not found in flyway_schema_history — is this the right database?';
    END IF;

    IF v39_desc = 'password reset request' THEN
        RAISE EXCEPTION 'V39 is already password_reset_request — fix already applied or wrong database';
    END IF;

    RAISE NOTICE 'V39 description: %. Proceeding with renumbering.', v39_desc;
END $$;

-- Step 2: Renumber V39-V42 → V40-V43 (reverse order to avoid unique constraint violation)
-- Also update the 'script' column to match the new filenames on disk.
UPDATE flyway_schema_history
  SET version = '43', script = 'V43__drop_security_audit_skill_version_fk.sql'
  WHERE version = '42';

UPDATE flyway_schema_history
  SET version = '42', script = 'V42__notification_system.sql'
  WHERE version = '41';

UPDATE flyway_schema_history
  SET version = '41', script = 'V41__security_audit_timestamptz.sql'
  WHERE version = '40';

UPDATE flyway_schema_history
  SET version = '40', script = 'V40__security_audit.sql'
  WHERE version = '39';

-- Step 3: Null out checksums so Flyway recalculates them from the (renamed) files.
-- The SQL content is identical, only the filename/version changed, so Flyway will
-- recompute and store the correct checksum on next startup.
UPDATE flyway_schema_history SET checksum = NULL WHERE version IN ('40', '41', '42', '43');

-- Step 4: Verify the renumbering
DO $$
DECLARE
    row_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO row_count
    FROM flyway_schema_history
    WHERE version IN ('40', '41', '42', '43');

    IF row_count <> 4 THEN
        RAISE EXCEPTION 'Expected 4 renumbered rows (V40-V43), found %', row_count;
    END IF;

    -- Verify V39 slot is now free for the new migration
    SELECT COUNT(*) INTO row_count
    FROM flyway_schema_history
    WHERE version = '39';

    IF row_count <> 0 THEN
        RAISE EXCEPTION 'V39 slot is not empty after renumbering!';
    END IF;

    RAISE NOTICE 'Renumbering successful: V40-V43 in place, V39 slot free for password_reset_request migration.';
END $$;

COMMIT;

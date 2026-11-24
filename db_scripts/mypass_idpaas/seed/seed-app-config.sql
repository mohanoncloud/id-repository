-- -------------------------------------------------------------------------------------------------
-- Seed for idpaas.app_config (MP-243): the first row for android and for ios.
--
-- Run ONCE per environment, after MP-380 has created mypass_idpaas. Until both rows exist, the app config
-- endpoint (GET /v1/enrolment/app/config) answers ENR-GEN-003 (503). Safe to run again: a platform that
-- already has a row is left alone (use an UPDATE to change it, see "Changing the config" below).
--
-- Why a script you run and not part of db_scripts: the store links differ per environment and aren't known
-- here, so nothing in this file is guessed. The script stops with an error if a value is missing.
--
-- Run as a login that can INSERT into idpaas.app_config: a member of idpaasconfigadmin, or postgres for the
-- very first seed. NOT idpaasuser (the service account only reads this table).
--
--   psql -h <db-host> -U <your-login> -d mypass_idpaas -f seed-app-config.sql \
--        -v actor='<your name or email>' \
--        -v android_store_url='<Play Store link>' \
--        -v ios_store_url='<App Store link>'
--
-- The values below are STARTING values, to be replaced by the decisions still open on MP-243 / MP-390:
--   * minimum_version 0.0.0: nobody is sent to the force-update screen yet. Raise it with an UPDATE when a
--     release must be forced.
--   * features: from the MP-243 example response; the agreed list and defaults come from Pragya.
--   * device_policy: the same values the app has built in (Android 10, iOS 16, no blocked models). The real
--     values are decision D11 (MP-390).
--   * maintenance is off. A message is required to switch it on (ck_appcfg_maintenance).
--
-- Changing the config later: always in one transaction that names you, or the trigger refuses it:
--   BEGIN;
--   SET LOCAL idpaas.actor = '<your name or email>';
--   UPDATE idpaas.app_config SET minimum_version = '1.3.0' WHERE platform = 'android';
--   COMMIT;
-- Every change gets a new config_version (the endpoint's ETag) and a row in idpaas.app_config_h.
-- -------------------------------------------------------------------------------------------------

\set ON_ERROR_STOP on

-- Stop with an error (non-zero exit) when a value is missing.
\if :{?actor}
\else
	DO $$ BEGIN RAISE EXCEPTION 'Missing -v actor=<your name or email> (recorded as cr_by)'; END $$;
\endif
\if :{?android_store_url}
\else
	DO $$ BEGIN RAISE EXCEPTION 'Missing -v android_store_url=<Play Store link>'; END $$;
\endif
\if :{?ios_store_url}
\else
	DO $$ BEGIN RAISE EXCEPTION 'Missing -v ios_store_url=<App Store link>'; END $$;
\endif

BEGIN;

-- The trigger takes cr_by from here (and sets cr_dtimes and config_version itself).
SET LOCAL idpaas.actor = :'actor';

INSERT INTO idpaas.app_config (platform, minimum_version, latest_version, store_url, features, device_policy)
VALUES
	('android', '0.0.0', NULL, :'android_store_url',
	 '{"showUin": false, "emailStepEnabled": true, "emailSkipAllowed": true, "galleryUploadAllowed": false}'::jsonb,
	 '{"policyVersion": "2026-11-24", "minOsVersion": "10", "blockedModels": []}'::jsonb),
	('ios', '0.0.0', NULL, :'ios_store_url',
	 '{"showUin": false, "emailStepEnabled": true, "emailSkipAllowed": true, "galleryUploadAllowed": false}'::jsonb,
	 '{"policyVersion": "2026-11-24", "minOsVersion": "16", "blockedModels": []}'::jsonb)
ON CONFLICT (platform) DO NOTHING;

COMMIT;

-- What is there now (and the history rows the trigger wrote).
SELECT platform, minimum_version, latest_version, store_url, maintenance_enabled, config_version, cr_by
  FROM idpaas.app_config ORDER BY platform;
SELECT platform, config_version, change_type, db_user FROM idpaas.app_config_h ORDER BY platform, config_version;

-- -------------------------------------------------------------------------------------------------
-- Database Name: mypass_idpaas
-- Table Name 	: idpaas.app_config
-- Purpose    	: App config: what the My Pass app reads at launch from the public config endpoint, one row
--                per platform (minimum and latest app version, store link, maintenance mode, feature
--                flags and the supported-device policy). Changed without a deploy; every change is kept
--                in idpaas.app_config_h.
--
-- Create By   	: MP-243
-- Created Date	: 20-Nov-2026
--
-- Modified Date        Modified By         Comments / Remarks
-- ------------------------------------------------------------------------------------------
--
-- ------------------------------------------------------------------------------------------

-- object: idpaas.app_config | type: TABLE --
-- DROP TABLE IF EXISTS idpaas.app_config CASCADE;
CREATE TABLE idpaas.app_config(
	platform character varying(16) NOT NULL,
	minimum_version character varying(32) NOT NULL,
	latest_version character varying(32),
	store_url character varying(512) NOT NULL,
	maintenance_enabled boolean NOT NULL DEFAULT FALSE,
	maintenance_message jsonb,
	maintenance_ends_dtimes timestamp,
	features jsonb NOT NULL DEFAULT '{}'::jsonb,
	device_policy jsonb,
	config_version integer NOT NULL DEFAULT 1,
	cr_by character varying(256) NOT NULL,
	cr_dtimes timestamp NOT NULL,
	upd_by character varying(256),
	upd_dtimes timestamp,
	CONSTRAINT pk_appcfg PRIMARY KEY (platform),
	CONSTRAINT ck_appcfg_platform CHECK (platform IN ('android', 'ios')),
	CONSTRAINT ck_appcfg_min_version CHECK (minimum_version ~ '^[0-9]+(\.[0-9]+)*$'),
	CONSTRAINT ck_appcfg_latest_version CHECK (latest_version IS NULL OR latest_version ~ '^[0-9]+(\.[0-9]+)*$'),
	CONSTRAINT ck_appcfg_features CHECK (jsonb_typeof(features) = 'object'),
	CONSTRAINT ck_appcfg_device_policy CHECK (device_policy IS NULL OR jsonb_typeof(device_policy) = 'object'),
	CONSTRAINT ck_appcfg_maintenance_message CHECK (maintenance_message IS NULL OR jsonb_typeof(maintenance_message) = 'object'),
	-- Maintenance can't be switched on without a message for the app to show.
	CONSTRAINT ck_appcfg_maintenance CHECK (NOT maintenance_enabled OR maintenance_message IS NOT NULL)

);
-- ddl-end --
COMMENT ON TABLE idpaas.app_config IS 'App config: what the My Pass app reads at launch, one row per platform. Changed without a deploy; every change is kept in idpaas.app_config_h.';
-- ddl-end --
COMMENT ON COLUMN idpaas.app_config.platform IS 'Platform: android or ios. Android and iOS are configured separately because their releases and store approvals happen at different times.';
-- ddl-end --
COMMENT ON COLUMN idpaas.app_config.minimum_version IS 'Minimum version: lowest app version allowed to run; below it the app shows the force-update screen. Dotted numeric, compared part by part (1.10.0 > 1.9.2).';
-- ddl-end --
COMMENT ON COLUMN idpaas.app_config.latest_version IS 'Latest version: optional; lets the app suggest an update without forcing it.';
-- ddl-end --
COMMENT ON COLUMN idpaas.app_config.store_url IS 'Store URL: Play Store or App Store link for this platform.';
-- ddl-end --
COMMENT ON COLUMN idpaas.app_config.maintenance_enabled IS 'Maintenance enabled: when TRUE the app shows the maintenance message and the enrolment endpoints reject requests.';
-- ddl-end --
COMMENT ON COLUMN idpaas.app_config.maintenance_message IS 'Maintenance message: approved text per language code, e.g. {"eng": "...", "ara": "..."}. Required while maintenance is on.';
-- ddl-end --
COMMENT ON COLUMN idpaas.app_config.maintenance_ends_dtimes IS 'Maintenance ends DateTimestamp: optional expected end of maintenance (UTC), shown to the citizen.';
-- ddl-end --
COMMENT ON COLUMN idpaas.app_config.features IS 'Features: on/off flags the app reads, e.g. {"showUin": false, "emailSkipAllowed": true}. The agreed list is in MP-243.';
-- ddl-end --
COMMENT ON COLUMN idpaas.app_config.device_policy IS 'Device policy (MP-244): {"policyVersion", "minOsVersion", "blockedModels": [{"manufacturer", "model", "reasonCode"}]}. minOsVersion is the OS release version (Android "14"), never an Android API level. Also checked at device registration (MP-247).';
-- ddl-end --
COMMENT ON COLUMN idpaas.app_config.config_version IS 'Config version: increased on every change; used for the response ETag so the app and caches see changes on next launch.';
-- ddl-end --
COMMENT ON COLUMN idpaas.app_config.cr_by IS 'Created By : ID or name of the user who create / insert record';
-- ddl-end --
COMMENT ON COLUMN idpaas.app_config.cr_dtimes IS 'Created DateTimestamp : Date and Timestamp when the record is created/inserted';
-- ddl-end --
COMMENT ON COLUMN idpaas.app_config.upd_by IS 'Updated By : ID or name of the user who update the record with new values';
-- ddl-end --
COMMENT ON COLUMN idpaas.app_config.upd_dtimes IS 'Updated DateTimestamp : Date and Timestamp when any of the fields in the record is updated with new values.';
-- ddl-end --

-- object: idpaas.app_config_h | type: TABLE --
-- DROP TABLE IF EXISTS idpaas.app_config_h CASCADE;
CREATE TABLE idpaas.app_config_h(
	platform character varying(16) NOT NULL,
	eff_dtimes timestamp NOT NULL,
	change_type character varying(8) NOT NULL,
	db_user character varying(256) NOT NULL,
	minimum_version character varying(32) NOT NULL,
	latest_version character varying(32),
	store_url character varying(512) NOT NULL,
	maintenance_enabled boolean NOT NULL,
	maintenance_message jsonb,
	maintenance_ends_dtimes timestamp,
	features jsonb NOT NULL,
	device_policy jsonb,
	config_version integer NOT NULL,
	cr_by character varying(256) NOT NULL,
	cr_dtimes timestamp NOT NULL,
	upd_by character varying(256),
	upd_dtimes timestamp,
	CONSTRAINT pk_appcfg_h PRIMARY KEY (platform, config_version)

);
-- ddl-end --
COMMENT ON TABLE idpaas.app_config_h IS 'App config history: one row per version of each platform''s config, written by trigger trg_appcfg_history, so the audit trail does not depend on whoever made the change remembering to record it.';
-- ddl-end --
COMMENT ON COLUMN idpaas.app_config_h.eff_dtimes IS 'Effective DateTimestamp : when this version of the config took effect (UTC).';
-- ddl-end --
COMMENT ON COLUMN idpaas.app_config_h.change_type IS 'Change type: INSERT or UPDATE.';
-- ddl-end --
COMMENT ON COLUMN idpaas.app_config_h.db_user IS 'DB user: database role that made the change (session_user), next to the person named in cr_by / upd_by.';
-- ddl-end --

-- object: idpaas.fn_appcfg_before_change | type: FUNCTION --
-- Keeps config_version, the timestamps and cr_by / upd_by right however the row is changed (script or psql).
-- The person making the change names themselves for the transaction, and the trigger takes the name from
-- there instead of from the column, which on UPDATE would still hold the previous editor's name:
--   BEGIN;
--   SET LOCAL idpaas.actor = '<your name or email>';
--   UPDATE idpaas.app_config SET ... WHERE platform = 'android';
--   COMMIT;
CREATE OR REPLACE FUNCTION idpaas.fn_appcfg_before_change() RETURNS trigger AS $$
DECLARE
	actor text := nullif(current_setting('idpaas.actor', true), '');
BEGIN
	IF actor IS NULL THEN
		RAISE EXCEPTION 'idpaas.app_config: run SET LOCAL idpaas.actor = ''<your name>'' in the same transaction before changing app config';
	END IF;
	IF TG_OP = 'INSERT' THEN
		NEW.cr_by := actor;
		NEW.cr_dtimes := (now() AT TIME ZONE 'UTC');
		NEW.config_version := 1;
		NEW.upd_by := NULL;
		NEW.upd_dtimes := NULL;
	ELSE
		-- platform is the key: changing it would remove the old platform's row (a delete in disguise) and
		-- clash with its history keys. Insert the other platform instead.
		IF NEW.platform IS DISTINCT FROM OLD.platform THEN
			RAISE EXCEPTION 'idpaas.app_config: platform cannot be changed (% to %)', OLD.platform, NEW.platform;
		END IF;
		-- An update that changes no config value is skipped (UPDATE 0): no new config_version, so the
		-- ETag stays the same and apps don't re-download, and no history row.
		IF (NEW.minimum_version, NEW.latest_version, NEW.store_url,
			NEW.maintenance_enabled, NEW.maintenance_message, NEW.maintenance_ends_dtimes,
			NEW.features, NEW.device_policy)
			IS NOT DISTINCT FROM
			(OLD.minimum_version, OLD.latest_version, OLD.store_url,
			OLD.maintenance_enabled, OLD.maintenance_message, OLD.maintenance_ends_dtimes,
			OLD.features, OLD.device_policy) THEN
			RETURN NULL;
		END IF;
		NEW.cr_by := OLD.cr_by;
		NEW.cr_dtimes := OLD.cr_dtimes;
		NEW.config_version := OLD.config_version + 1;
		NEW.upd_by := actor;
		NEW.upd_dtimes := (now() AT TIME ZONE 'UTC');
	END IF;
	RETURN NEW;
END;
$$ LANGUAGE plpgsql;
-- ddl-end --

-- object: idpaas.fn_appcfg_block_change | type: FUNCTION --
-- App config rows are never deleted (switch a feature or maintenance off with an UPDATE), and history is
-- append-only, so every version stays in idpaas.app_config_h.
CREATE OR REPLACE FUNCTION idpaas.fn_appcfg_block_change() RETURNS trigger AS $$
BEGIN
	RAISE EXCEPTION 'idpaas.%: % is not allowed; app config and its history are kept', TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;
-- ddl-end --

-- object: idpaas.fn_appcfg_history | type: FUNCTION --
-- SECURITY DEFINER: runs as the owner, so the history row is written although no login role has write
-- access to idpaas.app_config_h (see grants.sql). session_user is still the role that made the change.
CREATE OR REPLACE FUNCTION idpaas.fn_appcfg_history() RETURNS trigger
	SECURITY DEFINER SET search_path = idpaas, pg_temp AS $$
BEGIN
	INSERT INTO idpaas.app_config_h(
		platform, eff_dtimes, change_type, db_user,
		minimum_version, latest_version, store_url,
		maintenance_enabled, maintenance_message, maintenance_ends_dtimes,
		features, device_policy, config_version,
		cr_by, cr_dtimes, upd_by, upd_dtimes)
	VALUES (
		NEW.platform, (now() AT TIME ZONE 'UTC'), TG_OP, session_user,
		NEW.minimum_version, NEW.latest_version, NEW.store_url,
		NEW.maintenance_enabled, NEW.maintenance_message, NEW.maintenance_ends_dtimes,
		NEW.features, NEW.device_policy, NEW.config_version,
		NEW.cr_by, NEW.cr_dtimes, NEW.upd_by, NEW.upd_dtimes);
	RETURN NEW;
END;
$$ LANGUAGE plpgsql;
-- ddl-end --

-- object: trg_appcfg_before_change | type: TRIGGER --
CREATE TRIGGER trg_appcfg_before_change
	BEFORE INSERT OR UPDATE ON idpaas.app_config
	FOR EACH ROW EXECUTE FUNCTION idpaas.fn_appcfg_before_change();
-- ddl-end --

-- object: trg_appcfg_history | type: TRIGGER --
CREATE TRIGGER trg_appcfg_history
	AFTER INSERT OR UPDATE ON idpaas.app_config
	FOR EACH ROW EXECUTE FUNCTION idpaas.fn_appcfg_history();
-- ddl-end --

-- object: trg_appcfg_no_delete | type: TRIGGER --
CREATE TRIGGER trg_appcfg_no_delete
	BEFORE DELETE ON idpaas.app_config
	FOR EACH ROW EXECUTE FUNCTION idpaas.fn_appcfg_block_change();
-- ddl-end --

-- object: trg_appcfg_no_truncate | type: TRIGGER --
CREATE TRIGGER trg_appcfg_no_truncate
	BEFORE TRUNCATE ON idpaas.app_config
	FOR EACH STATEMENT EXECUTE FUNCTION idpaas.fn_appcfg_block_change();
-- ddl-end --

-- object: trg_appcfg_h_append_only | type: TRIGGER --
CREATE TRIGGER trg_appcfg_h_append_only
	BEFORE UPDATE OR DELETE ON idpaas.app_config_h
	FOR EACH ROW EXECUTE FUNCTION idpaas.fn_appcfg_block_change();
-- ddl-end --

-- object: trg_appcfg_h_no_truncate | type: TRIGGER --
CREATE TRIGGER trg_appcfg_h_no_truncate
	BEFORE TRUNCATE ON idpaas.app_config_h
	FOR EACH STATEMENT EXECUTE FUNCTION idpaas.fn_appcfg_block_change();
-- ddl-end --

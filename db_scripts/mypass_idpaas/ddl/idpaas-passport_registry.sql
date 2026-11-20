-- -------------------------------------------------------------------------------------------------
-- Database Name: mypass_idpaas
-- Table Name 	: idpaas.passport_registry
-- Purpose    	: Passport registry: passport hashes linked to applications, for the already-enrolled check
--                (ENR-PSP-009). A passport may have only one OPEN or ISSUED entry at a time.
--
-- Create By   	: MP-379
-- Created Date	: 19-Nov-2026
--
-- Modified Date        Modified By         Comments / Remarks
-- ------------------------------------------------------------------------------------------
--
-- ------------------------------------------------------------------------------------------

-- object: idpaas.passport_registry | type: TABLE --
-- DROP TABLE IF EXISTS idpaas.passport_registry CASCADE;
CREATE TABLE idpaas.passport_registry(
	passport_hash character varying(128) NOT NULL,
	application_id character varying(64) NOT NULL,
	status_code character varying(32) NOT NULL,
	cr_by character varying(256) NOT NULL,
	cr_dtimes timestamp NOT NULL,
	upd_by character varying(256),
	upd_dtimes timestamp,
	CONSTRAINT pk_pspreg PRIMARY KEY (passport_hash, application_id),
	CONSTRAINT fk_pspreg_enrapp FOREIGN KEY (application_id) REFERENCES idpaas.enrolment_application (application_id)

);
-- ddl-end --
-- Two enrolments with the same passport can't both be open or issued, even when they run at the same time.
CREATE UNIQUE INDEX uk_pspreg_active ON idpaas.passport_registry USING btree (passport_hash) WHERE status_code IN ('OPEN', 'ISSUED');
-- ddl-end --
COMMENT ON TABLE idpaas.passport_registry IS 'Passport registry: passport hashes linked to applications, for the already-enrolled check (ENR-PSP-009). A passport may have only one OPEN or ISSUED entry at a time.';
-- ddl-end --
COMMENT ON COLUMN idpaas.passport_registry.passport_hash IS 'Passport hash: HMAC-SHA256 over the normalised issuing state and passport number, key from keymanager. The plain passport number is never stored.';
-- ddl-end --
COMMENT ON COLUMN idpaas.passport_registry.application_id IS 'Application ID: the enrolment application that used this passport.';
-- ddl-end --
COMMENT ON COLUMN idpaas.passport_registry.status_code IS 'Status: OPEN (application in progress), ISSUED (identity created) or RELEASED (application rejected, abandoned or expired; no longer blocks).';
-- ddl-end --
COMMENT ON COLUMN idpaas.passport_registry.cr_by IS 'Created By : ID or name of the user who create / insert record';
-- ddl-end --
COMMENT ON COLUMN idpaas.passport_registry.cr_dtimes IS 'Created DateTimestamp : Date and Timestamp when the record is created/inserted';
-- ddl-end --
COMMENT ON COLUMN idpaas.passport_registry.upd_by IS 'Updated By : ID or name of the user who update the record with new values';
-- ddl-end --
COMMENT ON COLUMN idpaas.passport_registry.upd_dtimes IS 'Updated DateTimestamp : Date and Timestamp when any of the fields in the record is updated with new values.';
-- ddl-end --

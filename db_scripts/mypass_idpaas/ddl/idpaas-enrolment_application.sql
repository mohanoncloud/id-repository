-- -------------------------------------------------------------------------------------------------
-- Database Name: mypass_idpaas
-- Table Name 	: idpaas.enrolment_application
-- Purpose    	: Enrolment application: one row per My Pass enrolment, keyed by the Application ID (AID),
--                which is also the registrationId of the ID Repository draft.
--
-- Create By   	: MP-379
-- Created Date	: 19-Nov-2026
--
-- Modified Date        Modified By         Comments / Remarks
-- ------------------------------------------------------------------------------------------
--
-- ------------------------------------------------------------------------------------------

-- object: idpaas.enrolment_application | type: TABLE --
-- DROP TABLE IF EXISTS idpaas.enrolment_application CASCADE;
CREATE TABLE idpaas.enrolment_application(
	application_id character varying(64) NOT NULL,
	session_id character varying(64) NOT NULL,
	status_code character varying(32) NOT NULL,
	passport_hash character varying(128),
	declaration_version character varying(32),
	declaration_accepted_dtimes timestamp,
	cr_by character varying(256) NOT NULL,
	cr_dtimes timestamp NOT NULL,
	upd_by character varying(256),
	upd_dtimes timestamp,
	is_deleted boolean DEFAULT FALSE,
	del_dtimes timestamp,
	CONSTRAINT pk_enrapp PRIMARY KEY (application_id)

);
-- ddl-end --
CREATE INDEX idx_enrapp_session ON idpaas.enrolment_application USING btree (session_id);
-- ddl-end --
CREATE INDEX idx_enrapp_status ON idpaas.enrolment_application USING btree (status_code);
-- ddl-end --
COMMENT ON TABLE idpaas.enrolment_application IS 'Enrolment application: one row per My Pass enrolment, keyed by the Application ID (AID), which is also the registrationId of the ID Repository draft.';
-- ddl-end --
COMMENT ON COLUMN idpaas.enrolment_application.application_id IS 'Application ID: AID returned to the app; also the registrationId of the ID Repository draft.';
-- ddl-end --
COMMENT ON COLUMN idpaas.enrolment_application.session_id IS 'Session ID: enrolment session that owns the application (never the session token).';
-- ddl-end --
COMMENT ON COLUMN idpaas.enrolment_application.status_code IS 'Status: CONSENTED, EMAIL_VERIFIED, PASSPORT_VERIFIED, FACE_VERIFIED, SUBMITTED, PUBLISH_PENDING, ISSUED, UNDER_REVIEW, REJECTED, ASSISTED_DESK or EXPIRED (API spec section 5.2).';
-- ddl-end --
COMMENT ON COLUMN idpaas.enrolment_application.passport_hash IS 'Passport hash: HMAC-SHA256 over the normalised issuing state and passport number, key from keymanager. The plain passport number is never stored.';
-- ddl-end --
COMMENT ON COLUMN idpaas.enrolment_application.declaration_version IS 'Declaration version: version of the declaration accepted at submit.';
-- ddl-end --
COMMENT ON COLUMN idpaas.enrolment_application.declaration_accepted_dtimes IS 'Declaration accepted DateTimestamp: when the user accepted the declaration (UTC).';
-- ddl-end --
COMMENT ON COLUMN idpaas.enrolment_application.cr_by IS 'Created By : ID or name of the user who create / insert record';
-- ddl-end --
COMMENT ON COLUMN idpaas.enrolment_application.cr_dtimes IS 'Created DateTimestamp : Date and Timestamp when the record is created/inserted';
-- ddl-end --
COMMENT ON COLUMN idpaas.enrolment_application.upd_by IS 'Updated By : ID or name of the user who update the record with new values';
-- ddl-end --
COMMENT ON COLUMN idpaas.enrolment_application.upd_dtimes IS 'Updated DateTimestamp : Date and Timestamp when any of the fields in the record is updated with new values.';
-- ddl-end --
COMMENT ON COLUMN idpaas.enrolment_application.is_deleted IS 'IS_Deleted : Flag to mark whether the record is Soft deleted.';
-- ddl-end --
COMMENT ON COLUMN idpaas.enrolment_application.del_dtimes IS 'Deleted DateTimestamp : Date and Timestamp when the record is soft deleted with is_deleted=TRUE';
-- ddl-end --

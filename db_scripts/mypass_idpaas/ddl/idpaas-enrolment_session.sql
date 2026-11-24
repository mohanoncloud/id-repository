-- -------------------------------------------------------------------------------------------------
-- Database Name: mypass_idpaas
-- Table Name 	: idpaas.enrolment_session, idpaas.session_nonce, idpaas.session_proof_jti
-- Purpose    	: Enrolment sessions and their device binding (DPoP, RFC 9449), as specified in
--                "My Pass Enrolment Session API and Device-Bound Requests (Step 1)", section 6
--                (Confluence 55115777).
--
-- Create By   	: MP-246 / MP-247 / MP-248 / MP-249
-- Created Date	: 20-Nov-2026
--
-- Modified Date        Modified By         Comments / Remarks
-- ------------------------------------------------------------------------------------------
--
-- ------------------------------------------------------------------------------------------

-- object: idpaas.enrolment_session | type: TABLE --
-- DROP TABLE IF EXISTS idpaas.enrolment_session CASCADE;
CREATE TABLE idpaas.enrolment_session(
	session_id character varying(64) NOT NULL,
	token_hash character varying(128) NOT NULL,
	device_id character varying(64) NOT NULL,
	jkt character varying(64) NOT NULL,
	consent_id character varying(64) NOT NULL,
	application_id character varying(64),
	status_code character varying(32) NOT NULL,
	last_used_dtimes timestamp NOT NULL,
	expires_dtimes timestamp NOT NULL,
	closed_dtimes timestamp,
	cr_by character varying(256) NOT NULL,
	cr_dtimes timestamp NOT NULL,
	upd_by character varying(256),
	upd_dtimes timestamp,
	CONSTRAINT pk_enrses PRIMARY KEY (session_id),
	CONSTRAINT uk_enrses_token UNIQUE (token_hash),
	-- The session's own state only; the enrolment status lives in idpaas.enrolment_application.status_code.
	-- Exact values only: the closed check and uk_enrses_device_active depend on the exact value 'CLOSED'.
	CONSTRAINT ck_enrses_status CHECK (status_code IN ('ACTIVE', 'CLOSED')),
	CONSTRAINT ck_enrses_closed CHECK ((status_code = 'CLOSED') = (closed_dtimes IS NOT NULL))

);
-- ddl-end --
-- One device has one active session (spec 3.2 step 5), even when two requests race.
-- Every session not yet CLOSED counts, including expired ones that haven't been closed. When creating a
-- session, the service closes the device's previous session (expired or not) and inserts the new one in a
-- single transaction, and treats a uk_enrses_device_active violation as a concurrent request (MP-248).
CREATE UNIQUE INDEX uk_enrses_device_active ON idpaas.enrolment_session USING btree (device_id) WHERE status_code <> 'CLOSED';
-- ddl-end --
CREATE INDEX idx_enrses_application ON idpaas.enrolment_session USING btree (application_id);
-- ddl-end --
CREATE INDEX idx_enrses_expires ON idpaas.enrolment_session USING btree (expires_dtimes) WHERE status_code <> 'CLOSED';
-- ddl-end --
COMMENT ON TABLE idpaas.enrolment_session IS 'Enrolment session: one row per session created with POST /v1/enrolment/session, bound to the device key that signs every request (DPoP). One active session per device.';
-- ddl-end --
COMMENT ON COLUMN idpaas.enrolment_session.session_id IS 'Session ID: UUID. idpaas.enrolment_application.session_id holds it without a foreign key on purpose: applications are kept after their sessions are purged. idpaas.session_proof_jti references it (ON DELETE CASCADE).';
-- ddl-end --
COMMENT ON COLUMN idpaas.enrolment_session.token_hash IS 'Token hash: SHA-256 of the session token. The token itself is never stored or logged.';
-- ddl-end --
COMMENT ON COLUMN idpaas.enrolment_session.device_id IS 'Device ID: registered device (MP-152).';
-- ddl-end --
COMMENT ON COLUMN idpaas.enrolment_session.jkt IS 'JWK thumbprint: RFC 7638 thumbprint of the device key; every DPoP proof on this session must be signed with this key (ENR-SES-003).';
-- ddl-end --
COMMENT ON COLUMN idpaas.enrolment_session.consent_id IS 'Consent ID: consent given on this device for the current notice version (MP-151).';
-- ddl-end --
COMMENT ON COLUMN idpaas.enrolment_session.application_id IS 'Application ID: AID, set when the application is created (step 3); null before.';
-- ddl-end --
COMMENT ON COLUMN idpaas.enrolment_session.status_code IS 'Status: ACTIVE or CLOSED, the state of the session only. The enrolment status (API spec section 5.2) is kept only in idpaas.enrolment_application.status_code, the single source of truth.';
-- ddl-end --
COMMENT ON COLUMN idpaas.enrolment_session.last_used_dtimes IS 'Last used DateTimestamp: last authenticated request (UTC); drives the idle timeout.';
-- ddl-end --
COMMENT ON COLUMN idpaas.enrolment_session.expires_dtimes IS 'Expires DateTimestamp: absolute expiry (UTC); never extended by use.';
-- ddl-end --
COMMENT ON COLUMN idpaas.enrolment_session.closed_dtimes IS 'Closed DateTimestamp: when the session was closed (DELETE /v1/enrolment/session, replaced by a newer session, or expired), UTC.';
-- ddl-end --
COMMENT ON COLUMN idpaas.enrolment_session.cr_by IS 'Created By : ID or name of the user who create / insert record';
-- ddl-end --
COMMENT ON COLUMN idpaas.enrolment_session.cr_dtimes IS 'Created DateTimestamp : Date and Timestamp when the record is created/inserted';
-- ddl-end --
COMMENT ON COLUMN idpaas.enrolment_session.upd_by IS 'Updated By : ID or name of the user who update the record with new values';
-- ddl-end --
COMMENT ON COLUMN idpaas.enrolment_session.upd_dtimes IS 'Updated DateTimestamp : Date and Timestamp when any of the fields in the record is updated with new values.';
-- ddl-end --

-- object: idpaas.session_nonce | type: TABLE --
-- DROP TABLE IF EXISTS idpaas.session_nonce CASCADE;
CREATE TABLE idpaas.session_nonce(
	nonce character varying(64) NOT NULL,
	device_id character varying(64) NOT NULL,
	expires_dtimes timestamp NOT NULL,
	used_dtimes timestamp,
	cr_dtimes timestamp NOT NULL,
	CONSTRAINT pk_sesnonce PRIMARY KEY (nonce)

);
-- ddl-end --
CREATE INDEX idx_sesnonce_expires ON idpaas.session_nonce USING btree (expires_dtimes);
-- ddl-end --
COMMENT ON TABLE idpaas.session_nonce IS 'Session nonce: one-time nonces from POST /v1/enrolment/session/nonce. Consumed atomically: UPDATE ... SET used_dtimes = (now() AT TIME ZONE ''UTC'') WHERE nonce = ? AND device_id = ? AND used_dtimes IS NULL AND expires_dtimes > (now() AT TIME ZONE ''UTC''), and accept only if one row changed (ENR-SES-007 otherwise). The time columns hold UTC without a time zone, so compare with UTC as shown (or a UTC value bound from Java), never with bare now(), which uses the session TimeZone. Expired rows can be purged.';
-- ddl-end --
COMMENT ON COLUMN idpaas.session_nonce.nonce IS 'Nonce: 32 random bytes from SecureRandom, base64url (43 characters).';
-- ddl-end --
COMMENT ON COLUMN idpaas.session_nonce.device_id IS 'Device ID: the device the nonce was issued to; it is only valid for that device.';
-- ddl-end --
COMMENT ON COLUMN idpaas.session_nonce.expires_dtimes IS 'Expires DateTimestamp: issue time plus enrolment.session.nonce-ttl-seconds (UTC).';
-- ddl-end --
COMMENT ON COLUMN idpaas.session_nonce.used_dtimes IS 'Used DateTimestamp: when the nonce was consumed by POST /v1/enrolment/session; null while unused.';
-- ddl-end --

-- object: idpaas.session_proof_jti | type: TABLE --
-- DROP TABLE IF EXISTS idpaas.session_proof_jti CASCADE;
CREATE TABLE idpaas.session_proof_jti(
	session_id character varying(64) NOT NULL,
	jti character varying(64) NOT NULL,
	expires_dtimes timestamp NOT NULL,
	CONSTRAINT pk_sesjti PRIMARY KEY (session_id, jti),
	-- A jti only means something for an existing session; purging a session removes its jti rows.
	CONSTRAINT fk_sesjti_session FOREIGN KEY (session_id) REFERENCES idpaas.enrolment_session (session_id) ON DELETE CASCADE

);
-- ddl-end --
CREATE INDEX idx_sesjti_expires ON idpaas.session_proof_jti USING btree (expires_dtimes);
-- ddl-end --
COMMENT ON TABLE idpaas.session_proof_jti IS 'DPoP proof jti seen per session, for the replay check (spec section 5, check 5). INSERT ... ON CONFLICT DO NOTHING; no row inserted means a replay (ENR-SES-004). Works across pods. Rows past expires_dtimes can be purged. Can be replaced by a shared cache later.';
-- ddl-end --
COMMENT ON COLUMN idpaas.session_proof_jti.jti IS 'JTI: the proof''s jti claim (UUID).';
-- ddl-end --
COMMENT ON COLUMN idpaas.session_proof_jti.expires_dtimes IS 'Expires DateTimestamp: when the jti stops mattering, now plus enrolment.session.proof.jti-ttl-seconds (UTC).';
-- ddl-end --

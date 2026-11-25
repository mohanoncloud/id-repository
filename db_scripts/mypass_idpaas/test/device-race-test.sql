-- MP-247: manual test for idpaas.device on a real PostgreSQL 16. Not run by deploy.sh.
-- Needs the roles and schema, then idpaas-enrolment_session.sql and idpaas-device.sql (in that order) loaded, and
-- grants.sql's device and session lines applied. Run as a superuser:
--   psql -U postgres -f device-race-test.sql
-- Every line marked EXPECT must print the error or result shown. Everything is rolled back at the end.
\set ON_ERROR_STOP off
-- Three 43-character thumbprints, available as :'a', :'b' and :'c'.
SELECT repeat('A', 43) AS a, repeat('B', 43) AS b, repeat('C', 43) AS c \gset

BEGIN;

-- 1. Same key twice: the second insert must fail on uk_device_jkt.
INSERT INTO idpaas.device (device_id, jkt, public_key_jwk, platform, os_version, manufacturer, model, app_version, cr_by, cr_dtimes)
VALUES ('dev_t1', :'a', '{"kty":"EC"}', 'android', '14', 'Google', 'Pixel 8', '1.0.0', 'test', now() AT TIME ZONE 'UTC');
SAVEPOINT s1;
INSERT INTO idpaas.device (device_id, jkt, public_key_jwk, platform, os_version, manufacturer, model, app_version, cr_by, cr_dtimes)
VALUES ('dev_t2', :'a', '{"kty":"EC"}', 'android', '14', 'Google', 'Pixel 8', '1.0.0', 'test', now() AT TIME ZONE 'UTC'); -- EXPECT: ERROR uk_device_jkt
ROLLBACK TO s1;

-- 2. Same App Attest key ID with a different key: must fail on uk_device_attest_key.
INSERT INTO idpaas.device (device_id, jkt, public_key_jwk, app_attest_key_id, platform, os_version, model, app_version, cr_by, cr_dtimes)
VALUES ('dev_t3', :'b', '{"kty":"EC"}', 'KEYID_1', 'ios', '17.4', 'iPhone16,1', '1.0.0', 'test', now() AT TIME ZONE 'UTC');
SAVEPOINT s2;
INSERT INTO idpaas.device (device_id, jkt, public_key_jwk, app_attest_key_id, platform, os_version, model, app_version, cr_by, cr_dtimes)
VALUES ('dev_t4', :'c', '{"kty":"EC"}', 'KEYID_1', 'ios', '17.4', 'iPhone16,1', '1.0.0', 'test', now() AT TIME ZONE 'UTC'); -- EXPECT: ERROR uk_device_attest_key
ROLLBACK TO s2;

-- 3. Checks: Android with a key ID; REVOKED without a time or reason; a thumbprint that isn't 43 base64url characters.
SAVEPOINT s3;
INSERT INTO idpaas.device (device_id, jkt, public_key_jwk, app_attest_key_id, platform, os_version, model, app_version, cr_by, cr_dtimes)
VALUES ('dev_t5', :'c', '{"kty":"EC"}', 'K', 'android', '14', 'X', '1.0.0', 'test', now() AT TIME ZONE 'UTC'); -- EXPECT: ck_device_attest_key
ROLLBACK TO s3;
SAVEPOINT s4;
INSERT INTO idpaas.device (device_id, jkt, public_key_jwk, platform, os_version, model, app_version, status_code, cr_by, cr_dtimes)
VALUES ('dev_t6', :'c', '{"kty":"EC"}', 'android', '34', 'X', '1.0.0', 'REVOKED', 'test', now() AT TIME ZONE 'UTC'); -- EXPECT: ck_device_revoked (or ck_device_revoked_reason)
ROLLBACK TO s4;
SAVEPOINT s5;
INSERT INTO idpaas.device (device_id, jkt, public_key_jwk, platform, os_version, model, app_version, status_code, revoked_dtimes, cr_by, cr_dtimes)
VALUES ('dev_t7', :'c', '{"kty":"EC"}', 'android', '14', 'X', '1.0.0', 'REVOKED', now() AT TIME ZONE 'UTC', 'test', now() AT TIME ZONE 'UTC'); -- EXPECT: ck_device_revoked_reason
ROLLBACK TO s5;
SAVEPOINT s6;
INSERT INTO idpaas.device (device_id, jkt, public_key_jwk, platform, os_version, model, app_version, cr_by, cr_dtimes)
VALUES ('dev_t8', 'abcdef0123456789abcdef0123456789abcdef01', '{"kty":"EC"}', 'android', '14', 'X', '1.0.0', 'test', now() AT TIME ZONE 'UTC'); -- EXPECT: ck_device_jkt
ROLLBACK TO s6;

-- 4. Revoke trigger: stamps who and when itself, then the row is final.
SAVEPOINT s7;
UPDATE idpaas.device SET status_code = 'REVOKED' WHERE device_id = 'dev_t1'; -- EXPECT: ck_device_revoked_reason (no reason given)
ROLLBACK TO s7;
UPDATE idpaas.device SET status_code = 'REVOKED', revoked_reason = 'FRAUD_SUSPECTED', upd_by = 'someone else', revoked_dtimes = '2000-01-01'
 WHERE device_id = 'dev_t1';                                                -- EXPECT: UPDATE 1
SELECT device_id, status_code, revoked_reason, upd_by, revoked_dtimes > now() - interval '1 day' AS revoked_now, upd_dtimes IS NOT NULL AS upd_set
  FROM idpaas.device WHERE device_id = 'dev_t1';                           -- EXPECT: REVOKED, FRAUD_SUSPECTED, upd_by = your session user (not 'someone else'), true, true
SAVEPOINT s8;
UPDATE idpaas.device SET status_code = 'ACTIVE', revoked_dtimes = NULL, revoked_reason = NULL WHERE device_id = 'dev_t1'; -- EXPECT: ERROR ... is revoked and can not be changed
ROLLBACK TO s8;
SAVEPOINT s9;
UPDATE idpaas.device SET revoked_reason = 'OTHER' WHERE device_id = 'dev_t1'; -- EXPECT: ERROR ... is revoked and can not be changed
ROLLBACK TO s9;
SAVEPOINT s10;
UPDATE idpaas.device SET model = 'Hacked' WHERE device_id = 'dev_t3';        -- EXPECT: ERROR only status_code and revoked_reason can be changed
ROLLBACK TO s10;

-- 5. Foreign keys: a session or a nonce for an unknown device is refused; for a registered device it works.
SAVEPOINT s11;
INSERT INTO idpaas.session_nonce (nonce, device_id, expires_dtimes, cr_dtimes)
VALUES ('n1', 'dev_unknown', now() AT TIME ZONE 'UTC', now() AT TIME ZONE 'UTC'); -- EXPECT: ERROR fk_sesnonce_device
ROLLBACK TO s11;
SAVEPOINT s12;
INSERT INTO idpaas.enrolment_session (session_id, token_hash, device_id, jkt, consent_id, status_code, last_used_dtimes, expires_dtimes, cr_by, cr_dtimes)
VALUES ('s1', 'h1', 'dev_unknown', 'j', 'c', 'ACTIVE', now() AT TIME ZONE 'UTC', now() AT TIME ZONE 'UTC', 'test', now() AT TIME ZONE 'UTC'); -- EXPECT: ERROR fk_enrses_device
ROLLBACK TO s12;
INSERT INTO idpaas.session_nonce (nonce, device_id, expires_dtimes, cr_dtimes)
VALUES ('n2', 'dev_t3', now() AT TIME ZONE 'UTC', now() AT TIME ZONE 'UTC'); -- EXPECT: INSERT 0 1

ROLLBACK;

-- 6. Roles (as a superuser; after grants.sql's device lines):
--    SET ROLE idpaasuser; UPDATE idpaas.device SET status_code='REVOKED';           -- EXPECT: permission denied
--    SET ROLE idpaasdeviceadmin; UPDATE idpaas.device SET model='x';                 -- EXPECT: permission denied
--    SET ROLE idpaasdeviceadmin; UPDATE idpaas.device SET revoked_dtimes=now();      -- EXPECT: permission denied
--    SET ROLE idpaasdeviceadmin; UPDATE idpaas.device SET status_code='REVOKED', revoked_reason='SUPPORT_REQUEST' WHERE device_id='...'; -- EXPECT: UPDATE 1

-- 7. Concurrency (needs pgbench). Save the INSERT below as race.sql and run:
--      pgbench -n -c 20 -j 4 -t 1 -f race.sql postgres
--    The same jkt from 20 clients: exactly 1 row for it in idpaas.device and 19 unique_violation errors, never 2 rows:
--      \set n random(1, 1000000000)
--      INSERT INTO idpaas.device (device_id, jkt, public_key_jwk, platform, os_version, manufacturer, model, app_version, cr_by, cr_dtimes)
--      VALUES ('dev_race_' || :n, repeat('R', 43), '{"kty":"EC"}', 'android', '14', 'Google', 'Pixel 8', '1.0.0', 'test', now() AT TIME ZONE 'UTC');
--    Clean up as the table owner: DELETE FROM idpaas.device WHERE jkt = repeat('R', 43);
--    (Delete the row's sessions first if any.)

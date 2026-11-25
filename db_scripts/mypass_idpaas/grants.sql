\c mypass_idpaas 

GRANT CONNECT
   ON DATABASE mypass_idpaas
   TO idpaasuser;

GRANT USAGE
   ON SCHEMA idpaas
   TO idpaasuser;

GRANT SELECT,INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES
   ON ALL TABLES IN SCHEMA idpaas
   TO idpaasuser;

ALTER DEFAULT PRIVILEGES IN SCHEMA idpaas 
	GRANT SELECT,INSERT,UPDATE,DELETE,REFERENCES ON TABLES TO idpaasuser;

-- App config (MP-243): the service only reads it, so a pod or a leaked idpaasuser password can neither
-- change the config nor touch its history.
REVOKE INSERT,UPDATE,DELETE,TRUNCATE,REFERENCES
   ON idpaas.app_config, idpaas.app_config_h
   FROM idpaasuser;

-- Config editors change app_config and read its history. History rows are written only by the trigger
-- (fn_appcfg_history runs as its owner), so nobody needs write access to app_config_h.
GRANT CONNECT
   ON DATABASE mypass_idpaas
   TO idpaasconfigadmin;

GRANT USAGE
   ON SCHEMA idpaas
   TO idpaasconfigadmin;

GRANT SELECT,INSERT,UPDATE
   ON idpaas.app_config
   TO idpaasconfigadmin;

GRANT SELECT
   ON idpaas.app_config_h
   TO idpaasconfigadmin;

-- Devices (MP-247): the service registers and reads devices. It can't change or remove one, so a pod or a
-- leaked idpaasuser password can't un-revoke a key or edit a registered key.
REVOKE UPDATE,DELETE,TRUNCATE,REFERENCES
   ON idpaas.device
   FROM idpaasuser;

-- Device revokers set a key's status and reason and nothing else; trigger trg_device_before_update stamps who and when.
GRANT CONNECT
   ON DATABASE mypass_idpaas
   TO idpaasdeviceadmin;

GRANT USAGE
   ON SCHEMA idpaas
   TO idpaasdeviceadmin;

GRANT SELECT
   ON idpaas.device
   TO idpaasdeviceadmin;

GRANT UPDATE (status_code, revoked_reason)
   ON idpaas.device
   TO idpaasdeviceadmin;

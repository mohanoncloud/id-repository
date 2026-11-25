CREATE ROLE idpaasuser WITH 
	INHERIT
	LOGIN
	PASSWORD :dbuserpwd;

-- App config editors (MP-243). No login and no password: grant it to the named people who may change
-- idpaas.app_config (GRANT idpaasconfigadmin TO <person>). The service user only reads app config.
CREATE ROLE idpaasconfigadmin WITH
	NOLOGIN;

-- Device revokers (MP-247). No login and no password: grant it to the named people (support) who may revoke
-- a device key (GRANT idpaasdeviceadmin TO <person>). The service user can only register and read devices.
CREATE ROLE idpaasdeviceadmin WITH
	NOLOGIN;

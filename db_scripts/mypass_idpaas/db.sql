CREATE DATABASE mypass_idpaas 
	ENCODING = 'UTF8' 
	LC_COLLATE = 'en_US.UTF-8' 
	LC_CTYPE = 'en_US.UTF-8' 
	TABLESPACE = pg_default 
	OWNER = postgres
	TEMPLATE  = template0;
COMMENT ON DATABASE mypass_idpaas IS 'ID PAAS (My Pass enrolment API): applications and the passport hashes used for the already-enrolled check';

\c mypass_idpaas 

DROP SCHEMA IF EXISTS idpaas CASCADE;
CREATE SCHEMA idpaas;
ALTER SCHEMA idpaas OWNER TO postgres;
ALTER DATABASE mypass_idpaas SET search_path TO idpaas,pg_catalog,public;

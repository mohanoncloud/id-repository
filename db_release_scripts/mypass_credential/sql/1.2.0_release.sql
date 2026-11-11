-- -------------------------------------------------------------------------------------------------
-- Database Name: mypass_credential
-- Release Version 	: 1.2
-- Purpose    		: Database Alter scripts for the release for credential DB.       
-- Create By   		: Sadanandegowda DM
-- Created Date		: Sep-2020
-- 
-- Modified Date        Modified By         Comments / Remarks
-- -------------------------------------------------------------------------------------------------
-- Apr-2021 		Ram Bhatt 	    status_comment column added.
----------------------------------------------------------------------------------------------------

\c mypass_credential sysadmin

ALTER TABLE credential.credential_transaction ADD COLUMN status_comment character varying(512) ;

---------------------------------------------------------------------------------------------------

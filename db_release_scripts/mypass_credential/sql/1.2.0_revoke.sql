-- -------------------------------------------------------------------------------------------------
-- Database Name: mypass_credential
-- Release Version 	: 1.2
-- Purpose    		: Revoking Database Alter deployement done for release in mypass_credential DB.       
-- Create By   		: Sadanandegowda DM
-- Created Date		: Sep-2020
-- 
-- Modified Date        Modified By         Comments / Remarks
-- -------------------------------------------------------------------------------------------------

\c mypass_credential sysadmin

ALTER TABLE credential.credential_transaction DROP COLUMN status_comment ;

-----------------------------------------------------------------------------------------------------

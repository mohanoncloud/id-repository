# Databases

## Overview
This folder containers various SQL scripts to create database and tables in postgres. The tables are described under `<db name>/ddl/`. Default data that's populated in the tables is present under `<db name>/dml` folder 

This folder containers various SQL scripts to create database and tables in postgres.  These scripts are automatically run with as part of DB initialisation in [Sandbox Deployment](https://docs.mosip.io/1.2.0/deployment/sandbox-deployment) 

Developers may run the SQLs using `<db name>/deploy.sh` script.


## Migrating an existing database (MP-106)

An environment that already has the old `mosip_*` database with data must rename it, not run `deploy.sh`: `deploy.sh` drops and recreates the database, so the existing data would be lost. Roles, schemas, tables and grants are unchanged by the rename.

| Old name | New name | mosip-config files that hold its JDBC URL |
|---|---|---|
| `mosip_idrepo` | `mypass_idrepo` | `id-repository-default.properties` |
| `mosip_idmap` | `mypass_idmap` | `id-repository-default.properties` |
| `mosip_credential` | `mypass_credential` | `id-repository-default.properties` |

1. Stop every service that connects to the database (scale its deployments to 0), so no connections are open.
2. As the postgres super user, rename each database: `ALTER DATABASE <old name> RENAME TO <new name>;`
3. Switch the JDBC URLs in the mosip-config files above to the new name (part of the single mosip-config change for MP-106), then start the services.

Rollback: stop the services, rename back with `ALTER DATABASE <new name> RENAME TO <old name>;`, revert the mosip-config change, and start the services.

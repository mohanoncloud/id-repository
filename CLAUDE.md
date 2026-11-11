# CLAUDE.md

Guidance for Claude Code (claude.ai/code) and the Claude PR review in this repository. Cross-repo context (service map, template, other repos) is in `../CLAUDE.md` when this repo is cloned in the BHS-BS workspace.

## Project context

- **What it is:** BHS-BS fork of MOSIP **id-repository**, version **1.3.1**. Part of the My Pass core platform.
- **What it does:** stores each person's foundational identity (demographics, biometrics, documents) keyed by UIN, manages VIDs, and issues credentials to partners (IDA, digital card, print).
- **Remotes:** `origin` is `github.com/BHS-BS/id-repository`; upstream is `mosip/id-repository`.

## Architecture overview

The Maven parent is **`id-repository/pom.xml`**, not the repo root (`io.mosip.idrepository:id-repository-parent:1.3.1`).

| Module (`id-repository/…`) | Kind | Port | Context path | Config names | DB |
|---|---|---|---|---|---|
| `id-repository-core` | shared library | — | — | — | — |
| `id-repository-identity-service` | Spring Boot | 8090 | `/idrepository/v1/identity` | `application,id-repository` | `mypass_idrepo` |
| `id-repository-vid-service` | Spring Boot | 8091 | `/idrepository/v1` | `application,id-repository` | `mypass_idmap` |
| `credential-request-generator` | Spring Boot | 8094 | `/v1/credentialrequest` | `application,credential-request,id-repository` | `mypass_credential` |
| `credential-service` | Spring Boot | 8095 | `/v1/credentialservice` | `application,credential-service,id-repository` | — |
| `id-repository-salt-generator` | Spring Batch job | 8092 | — | `id-repository,application,kernel` | `mypass_idrepo`, `mypass_idmap` |

- **Configuration:** each module has only `bootstrap.properties`; everything else comes from **mosip-config** through Spring Cloud Config. DB names are `mosip.idrepo.db.identity.db-name`, `mosip.idrepo.db.vid.db-name` and `mosip.credential.service.jdbc.url` in `id-repository-default.properties`.
- **id-repository-core** holds what crosses services:
  - `spi/` interfaces implemented by the services.
  - `security/IdRepoSecurityManager`: UIN hashing and encryption through keymanager, using the `uin_hash_salt` / `uin_encrypt_salt` tables.
  - `config/IdRepoDataSourceConfig`: base datasource config (`mosip.idrepo.db.pool.*`). Sharding classes exist but sharding is disabled.
  - `manager/CredentialServiceManager`, `CredentialStatusManager`: credential issuance and status. Issuance changes belong here.
  - `RestHelper` / `RestRequestBuilder` with URLs keyed by `RestServicesConstants`.
- **Identity service** (`IdRepoController`, `IdRepoDraftController`, `VidEventCallbackController`):
  - `IdRepoProxyServiceImpl` resolves the id type (UIN/VID/RID/HANDLE), checks duplicates, builds responses and publishes `IDENTITY_CREATED` / `IDENTITY_UPDATED` on WebSub.
  - `IdRepoServiceImpl` persists. Its class-level `@Transactional` is commented out, and object-store uploads run before DB writes on purpose. Keep that order.
  - `IdRepoDraftServiceImpl` (drafts for Registration Processor) is `@Transactional`; long REST/S3 calls inside it use `Propagation.NOT_SUPPORTED`. Follow the same pattern.
  - Biometrics (CBEFF) and documents are encrypted in S3/MinIO; only metadata and hashes are in Postgres.
- **Credential pipeline (asynchronous):** create/update writes `credential_request_status` rows → a scheduled `CredentialStatusManager` job → credential-request-generator (Spring Batch, `mypass_credential`) → credential-service builds the credential (`IdAuthProvider`, `QrCodeProvider`, `VerCredProvider`) and delivers it over WebSub.
- **Services it calls:** keymanager, kernel ID generator, masterdata/syncdata (ID schema), PMS (partners), WebSub hub, audit-manager, the biometric SDK, datashare. Tokens through `kernel-auth-adapter`.
- **Databases:** fresh install in `db_scripts/mypass_{idrepo,idmap,credential}/`, upgrades in `db_upgrade_scripts/`, legacy 1.1.x–1.2.1 release/revoke scripts in `db_release_scripts/`.
- **Other folders:** `api-test/` (separate Maven project, REST Assured/TestNG, runs against a deployed environment), `helm/` (credential, credentialfeeder, credentialrequest, identity, idrepo umbrella, idrepo-saltgen, vid), `deploy/` (install/delete/restart scripts, namespace `idrepo`), `docs/`.

## Coding standards and conventions

- Java 21, MOSIP `kernel-bom` **1.3.1** (Spring Boot 3.2.x), springdoc 2.5.0. Lombok is used.
- Keep the upstream packages (`io.mosip.idrepository.*`, `io.mosip.credential.*`, `io.mosip.credentialstore.*`) and groupIds.
- **Errors:** `IdRepoErrorConstants` in core (`IDR-IDC-0xx` request/DB, `IDR-IDS-0xx` crypto/object store/schema, `IDR-VID-0xx` VID). Exceptions extend kernel-core `BaseCheckedException` / `BaseUncheckedException`; `@ControllerAdvice` handlers map them to `ResponseWrapper` `errors[]`.
- **Logging:** `IdRepoLogger` (kernel-core `Logger`). Don't add SLF4J loggers directly.
- **Auth:** endpoints use `@PreAuthorize("hasAnyRole(@authorizedRoles.getXxx())")` with role lists from `mosip.role.idrepo.*`. A new endpoint needs a field in `AuthorizedRolesDto`, the property in mosip-config and in the test properties.
- **Data protection, don't bypass:**
  - `uin.uin_hash` = `<saltId>_<SHA-256(uin+salt)>`; lookups always go through it.
  - `IdRepoEntityInterceptor` encrypts `uin` and `uin_data` on save and decrypts plus hash-checks on load (`IDR-IDS-001` on mismatch).
  - Object-store files are encrypted with keymanager ref ids and hash-checked on read (`IDR-IDS-002`).
  - `AuthTypeStatusImpl` keys `uin_auth_lock` by an **unsalted** hash, unlike everything else.
- New `@Async` work goes to the matching named executor in `IdRepoConfig`; use `withSecurityContext` when the async code makes authenticated REST calls.
- Schema changes need DDL in `db_scripts/mypass_<db>/ddl/` and an upgrade/rollback pair in `db_upgrade_scripts/mypass_<db>/sql/`. Hibernate `ddl-auto` is `none`.

## Build and run

Run from `id-repository/`:

```bash
cd id-repository
mvn clean install -Dgpg.skip=true -Dmaven.javadoc.skip=true                        # build + tests
mvn clean install -Dgpg.skip=true -Dmaven.javadoc.skip=true -Dmaven.test.skip=true  # no tests
mvn install -Dgpg.skip=true -Dmaven.test.skip=true -pl id-repository-core          # after a core change
```

- `-DskipTests` does **not** skip tests: the parent surefire config hardcodes `skipTests=false`. Use `-Dmaven.test.skip=true`.
- Every service depends on `id-repository-core`. Use `-am`, or install core first, when building one module.
- **Docker:** after `install`, `docker build -t <svc> id-repository/<module>`. The Dockerfile copies `target/<svc>-*.jar`.
- **Running a service needs a platform.** The container `CMD` downloads `kernel-auth-adapter.jar` from `iam_adapter_url_env` and starts with `-Dloader.path`, `-Dspring.cloud.config.uri/label` and `-Dspring.profiles.active`. It needs the config server, Postgres, Keycloak, keymanager and (identity service) S3/MinIO plus a biometric SDK jar. `application-local1.properties` in the identity service is an outdated example.
- `api-test/` is not in the reactor: `cd api-test && mvn clean install -Dgpg.skip=true`. It runs against a live environment.
- The root `deploy.sh` is an old `mvn deploy` helper, not a DB script.

## Testing

```bash
cd id-repository
mvn test -Dgpg.skip=true                                                                  # all
mvn test -Dgpg.skip=true -pl id-repository-identity-service -am -DfailIfNoTests=false -Dtest=IdRepoServiceTest
mvn test -Dgpg.skip=true -pl id-repository-vid-service -Dtest=VidServiceImplTest#methodName   # core already installed
```

- About 80 test classes (core 24, credential-request-generator 18, credential-service 17, identity 16, vid 5, salt-generator 3). JUnit 4 with `SpringRunner`, Mockito and some PowerMock.
- The parent surefire argLine carries `--add-opens` flags for PowerMock on Java 21. Run tests through Maven or copy the flags into the IDE.
- Test config is each module's `src/test/resources/application*.properties`. A new `@Value` property must be added there, or the test context fails to start.
- No Postgres, config server or network needed.
- **Flaky:** `VidDraftHelperTest.activeDraftVidExceptionTest` (identity service) mocks a request built with the current timestamp, so whether it throws depends on timing. It can pass and fail on the same code. Rerun before treating it as a real failure.

## Code quality and linting

- No checkstyle, spotless or formatter is configured. Follow the surrounding style.
- JaCoCo runs with surefire; it excludes `constant`, `config`, `dto`, `entity`, `repository` and `httpfilter`. Sonar is not wired into the BHS CI.
- CI (`.github/workflows/`):
  - `push-trigger.yml` runs on PRs, `workflow_dispatch` and pushes to `develop`/`main`.
    - A `changes` job picks which service images the change needs.
    - `BHS-BS/shared-workflow` `maven-build.yml@develop` builds `./id-repository`, then `docker-build.yml@develop` builds each changed service and pushes it to GHCR.
    - **`ci-gate`** is the required check. The api-test compile job is commented out.
  - `db-test.yml` (one matrix entry per DB), `chart-lint-publish.yml` and `clear-artifacts.yml` still call upstream `mosip/kattu`.
  - `chart-lint-publish.yml` only lints on push (`CHART_PUBLISH` defaults to `NO`): publishing pushes to `BHS-BS/mosip-helm`, which has no working token, and failed after the audit-manager merge. A manual run can still set `CHART_PUBLISH=YES`.
  - The DB test workflow keeps its legacy push patterns (`release*`, `master`, `1.*`, `develop*`) plus `main`, on purpose for now, so it does not yet follow the BHS workflow rule.

## Branch and PR conventions

- Working branch is `develop`. `main` only gets changes through the next `develop` merge.
- Branch from `develop`, named after the Jira key (e.g. `MP-111`).
- PR into `develop`, titled `[MP-xx] <summary>`. Squash-merged (adds `(#n)`). Commit messages start with the Jira key.
- Before merge: lead review (no CODEOWNERS yet) and a green `ci-gate`. The Claude review (MP-83) is not yet set up as a workflow in this repo.
- If a PR changes build, test or conventions, update this file in the same PR.

## BHS rules (apply to every BHS-BS repo)

- **GA versions only.** No `-SNAPSHOT` dependencies, parents or plugins, and no new snapshot repositories.
- **No secrets in code or config.** Passwords, client secrets and keys come from environment or config-server placeholders (e.g. `${db.dbuser.password}`). Never commit real values, including in `application-local*.properties`, test resources, Helm values or scripts.
- **Keycloak realm is `mypass`.**
- **DB upgrade scripts chain without gaps.** Every `X_to_Y_upgrade.sql` has a matching `X_to_Y_rollback.sql`, and each step's `Y` is the next step's `X`. Schema changes also update the fresh-install DDL in `db_scripts/`.
- **Workflows trigger on real branches only** (`develop`, `main`, PRs; no upstream `MOSIP*`/`release*`/`1.*` patterns), and every job sets `timeout-minutes`.

## Pitfalls

- **`db_scripts/mypass_*/deploy.sh` drops the database and role**, then recreates them. Never run it against a shared DB.
- **Upgrade chain** for all three DBs: `1.1.5.5 → 1.2.0.1-B1 → B2 → B3 → 1.2.0.1 → 1.2.1.0 → 1.2.2.0 → … → 1.2.2.4 → 1.3.0 → 1.3.1`. A new version needs a `1.3.1_to_<next>` pair per DB.
- **MP-106 rename (`mosip_*` → `mypass_*`).**
  - Done here: `db_scripts/`, `db_upgrade_scripts/`, `db_release_scripts/` (`MYPASS_DB_NAME`), `db-test.yml`, Dockerfile users, and DB names in local/test properties. The Helm charts and `deploy/` are not renamed (see "Helm charts are upstream").
  - Still pending and must deploy with these scripts: **mosip-config `id-repository-default.properties`** (`mosip.idrepo.db.identity.db-name`, `mosip.idrepo.db.vid.db-name`, `mosip.credential.service.jdbc.url`).
  - Not renamed on purpose: roles (`idrepouser`, `idmapuser`, `credentialuser`), schemas, `io.mosip` packages, `mosip.*` property keys, the `api-internal.sandbox.mosip.net` host in `db_scripts/*/deploy.properties`.
- **Reusable workflows** live only in `mosip/kattu`; `mypass/kattu` does not exist. Don't rename `uses:` paths.
- **Container user.** Every Dockerfile's container user is `mypass`: uid 1002 for identity, vid, credential-request-generator and credential-service; 1001 for salt-generator and api-test. The identity service and api-test still build `FROM mosipid/openjdk-21-jre:21.0.4`; `mypassid/openjdk-21-jre` does not exist yet.
- **Helm charts and deploy-time images are upstream, on purpose.** `helm/` keeps the upstream values (`docker.io` / `mosipid/*` images incl. credentials-feeder, `runAsUser: mosip`, `mosipid/os-shell`), and `deploy/idrepo-apitestrig` keeps `mosipid/apitest-idrepo`. The lead decided (2026-11-10) that DevOps applies Helm changes at deployment/release, so don't change them in feature PRs.
  - The templates pass `runAsUser` into the env var `container_user`, but the images don't read it at runtime, so `mosip` there next to the image user `mypass` is harmless. Keep it a user name, not a number.
  - CI pushes the images to GHCR (`ghcr.io/bhs-bs/<module>:develop`, private, needs a `ghcr.io` pull secret); pointing the charts at them is DevOps's change.
- **Helm repos.** `deploy/*/install.sh` install the upstream charts from `mosip/...` (`https://mosip.github.io/mosip-helm`), and the `helm/idrepo` umbrella pulls its subcharts from the same repo. `deploy/` is upstream on purpose: DevOps owns it together with Helm (lead decision 2026-11-10).
- **Global configmap key.** `deploy/idrepo-apitestrig/install.sh` reads `mosip-api-internal-host` from the `global` configmap; the cluster configmap must use that name.
- **`@PreAuthorize` is commented out** on the credential-request-generator endpoints. Treat a change there as a security change.
- **Draft create query parameter is case-sensitive:** `POST /draft/create/{rid}?UIN=`; `?uin=` is ignored and starts a new-registration draft.
- **`AnonymousProfileHelper` is a singleton with mutable request fields.** Concurrent requests can interleave its values.
- **Committed test credentials/hosts:** some test properties carry upstream MOSIP IPs and sample values. Don't copy them and don't add new ones.
- **Credential feeder:** `helm/credentialfeeder` and `deploy/credential-feeder` remain, but the feeder code moved upstream to `mosip-utilities`.

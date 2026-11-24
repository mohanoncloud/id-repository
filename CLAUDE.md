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
| `id-repository-paas-service` | Spring Boot | 8096 | `/v1/enrolment` | `application,id-paas` | `mypass_idpaas` (scripts only, not wired yet) |

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
- **ID PAAS** (`id-repository-paas-service`, MP-379): the My Pass enrolment API (`/v1/enrolment/*`, contract: My Pass Enrolment API Specification) the app calls.
  - Depends only on `id-repository-core`, never on identity-service classes, so it can move to its own repo. It calls the identity service's draft APIs over REST like any other client.
  - The kernel auth adapter lets every path and method through: `mosip.service.end-points=/**` plus `mosip.service.exclude.auth.allowed.method=GET,POST,PUT,PATCH,DELETE` (the adapter's default is GET only, so without it every POST gets a `KER-ATH-*` 401). Actuator exposes only `info,health`, since nothing protects it. `EnrolmentSessionFilter` authenticates with the session token and device signature instead; header names and the token scheme are in `mosip.idpaas.session.*`.
  - Errors are the spec's `ENR-*` codes and messages in `constant/EnrolmentErrorCode`; the app shows `errors[].message` word for word. Business failures are HTTP 200 with `errors[]`.
  - `spi/` interfaces (OCR, image quality, AID generator, ID Repository draft client, passport hash) have stubs in `spi/stub` that fail with `ENR-GEN-003` (503) until each is decided. `StubSessionVerifier` accepts any token, so it loads only with `mosip.idpaas.session.stub-enabled=true` (tests and dev config); without it or a real verifier the service does not start.
  - Passport images are never stored. The DataSource/JPA auto-configurations are excluded until a task persists data.
  - App config (`idpaas.app_config`, MP-243) is read-only for `idpaasuser`. People in the `idpaasconfigadmin` role (no login) change it with `BEGIN; SET LOCAL idpaas.actor = '<name>'; UPDATE ...; COMMIT;`. Without the actor the trigger rejects the change. Rows can't be deleted, and `app_config_h` is append-only, written only by the trigger.
  - `mypass_idpaas` time columns are `timestamp` holding UTC: compare with `now() AT TIME ZONE 'UTC'` (or a bound UTC value), never bare `now()`. It is fresh-install only until MP-380 provisions it, so it has no `db_upgrade_scripts` pair yet.
- **Credential pipeline (asynchronous):** create/update writes `credential_request_status` rows → a scheduled `CredentialStatusManager` job → credential-request-generator (Spring Batch, `mypass_credential`) → credential-service builds the credential (`IdAuthProvider`, `QrCodeProvider`, `VerCredProvider`) and delivers it over WebSub.
- **Services it calls:** keymanager, kernel ID generator, masterdata/syncdata (ID schema), PMS (partners), WebSub hub, audit-manager, the biometric SDK, datashare. Tokens through `kernel-auth-adapter`.
- **Databases:** fresh install in `db_scripts/mypass_{idrepo,idmap,credential,idpaas}/`, upgrades in `db_upgrade_scripts/`, legacy 1.1.x–1.2.1 release/revoke scripts in `db_release_scripts/`.
- **Other folders:** `api-test/` (separate Maven project, REST Assured/TestNG, runs against a deployed environment), `helm/` (credential, credentialfeeder, credentialrequest, identity, idrepo umbrella, idrepo-saltgen, vid; published to `oci://ghcr.io/bhs-bs/charts/<chart>`), `deploy/` (install/delete/restart scripts, namespace `idrepo`), `docs/`.

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

- About 90 test classes (core 24, credential-request-generator 18, credential-service 17, identity 16, paas 10, vid 5, salt-generator 3). JUnit 4 with `SpringRunner`, Mockito and some PowerMock.
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
  - `db-test.yml` (one matrix entry per DB) and `clear-artifacts.yml` still call upstream `mosip/kattu`.
  - `chart-lint-publish.yml` calls `BHS-BS/shared-workflow` `helm-lint-publish.yml@develop` (MP-206). PRs that change `helm/` lint the charts; a merge into `develop` or `release*` that changes `helm/` publishes them to `oci://ghcr.io/bhs-bs/charts/<chart>`. `release*` is kept on purpose for release branches.
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
  - Done here: `db_scripts/`, `db_upgrade_scripts/`, `db_release_scripts/` (`MYPASS_DB_NAME`), `db-test.yml`, Dockerfile users, and DB names in local/test properties. The Helm charts and `deploy/` are not renamed (see "Helm charts are owned by DevOps").
  - Still pending and must deploy with these scripts: **mosip-config `id-repository-default.properties`** (`mosip.idrepo.db.identity.db-name`, `mosip.idrepo.db.vid.db-name`, `mosip.credential.service.jdbc.url`).
  - Not renamed on purpose: roles (`idrepouser`, `idmapuser`, `credentialuser`), schemas, `io.mosip` packages, `mosip.*` property keys, the `api-internal.sandbox.mosip.net` host in `db_scripts/*/deploy.properties`.
- **Upstream reusable workflows** live in `mosip/kattu`; `mypass/kattu` does not exist, so don't rename those `uses:` paths. BHS workflows live in `BHS-BS/shared-workflow`.
- **Container user.** Every Dockerfile's container user is `mypass`: uid 1002 for identity, vid, paas, credential-request-generator and credential-service; 1001 for salt-generator and api-test. The identity service and api-test still build `FROM mosipid/openjdk-21-jre:21.0.4`; `mypassid/openjdk-21-jre` does not exist yet.
- **Helm charts are owned by DevOps; deploy-time images are upstream.** DevOps moved the charts to GHCR in MP-206: version `0.0.1-develop`, images `ghcr.io/bhs-bs/<image>:develop`. CI doesn't build the credentials feeder, so `helm/credentialfeeder` keeps `mosipid/id-repository-credentials-feeder`. `helm/` keeps the other upstream values (`runAsUser: mosip`, `mosipid/os-shell`), and `deploy/idrepo-apitestrig` keeps `mosipid/apitest-idrepo`. The lead decided (2026-11-10) that DevOps applies Helm changes at deployment/release, so don't change them in feature PRs.
  - The templates pass `runAsUser` into the env var `container_user`, but the images don't read it at runtime, so `mosip` there next to the image user `mypass` is harmless. Keep it a user name, not a number.
  - CI pushes the images to GHCR (`ghcr.io/bhs-bs/<module>:develop`, private, needs a `ghcr.io` pull secret); the charts use them.
- **Helm repos.** `deploy/*/install.sh` install the upstream charts from `mosip/...` (`https://mosip.github.io/mosip-helm`), The `helm/idrepo` umbrella bundles the subcharts next to it (`file://../<chart>`, version `0.0.1-develop`), so it no longer needs the MOSIP chart repo. `deploy/` is upstream on purpose: DevOps owns it together with Helm (lead decision 2026-11-10).
- **Global configmap key.** `deploy/idrepo-apitestrig/install.sh` reads `mosip-api-internal-host` from the `global` configmap; the cluster configmap must use that name.
- **`@PreAuthorize` is commented out** on the credential-request-generator endpoints. Treat a change there as a security change.
- **Draft create query parameter is case-sensitive:** `POST /draft/create/{rid}?UIN=`; `?uin=` is ignored and starts a new-registration draft.
- **`AnonymousProfileHelper` is a singleton with mutable request fields.** Concurrent requests can interleave its values.
- **Committed test credentials/hosts:** some test properties carry upstream MOSIP IPs and sample values. Don't copy them and don't add new ones.
- **Credential feeder:** `helm/credentialfeeder` and `deploy/credential-feeder` remain, but the feeder code moved upstream to `mosip-utilities`.

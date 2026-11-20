# ID PAAS (id-repository-paas-service)

The My Pass enrolment API that the app calls: `/v1/enrolment/*`. The contract is the **My Pass Enrolment API Specification**. ID PAAS calls the ID Repository draft APIs itself, so the app never talks to MOSIP directly.

- Port `8096`, context path `/v1/enrolment`, config names `application,id-paas`.
- Swagger UI: `http://<host>:8096/v1/enrolment/swagger-ui/index.html` (no session needed; OpenAPI JSON at `/v1/enrolment/v3/api-docs`).
- Depends only on `id-repository-core`, never on identity-service classes, so it can move to its own repository later.

## What's in place (MP-379 scaffold)

| Piece | Status |
|---|---|
| `POST /v1/enrolment/passport` | Parses the multipart request (`frontImage`, `backImage`, `request`) and the MOSIP envelope. The passport checks come with MP-256 / MP-261 / MP-257 / MP-258; until then the endpoint answers `ENR-GEN-003` (HTTP 503). |
| Errors | All `ENR-PSP-*`, `ENR-SES-*` and `ENR-GEN-*` codes and messages from the spec, in `constant/EnrolmentErrorCode`. Business failures are HTTP 200 with `errors[]`. |
| Session check | `EnrolmentSessionFilter`: `Authorization: Bearer <sessionToken>` + `X-Device-Signature` (header names and the token scheme in `mosip.idpaas.session.*`). Unexpected verifier errors return `ENR-GEN-003` with `errors[]`. **`StubSessionVerifier` accepts any token**, so it loads only with `mosip.idpaas.session.stub-enabled=true` (tests and dev config). Without it or a real verifier the service does not start. |
| Stubs (`spi/stub`) | OCR, image quality, AID generator, ID Repository draft client and passport hash. Each fails with `ENR-GEN-003` (503), except the AID stub, which returns a RID-shaped id with placeholder centre and machine IDs. |
| Database | `db_scripts/mypass_idpaas` (provisioned by DevOps, MP-380). Not wired into the service yet. |

## Auth

The kernel auth adapter is loaded at runtime (as in the other services) for outbound service tokens, but lets every inbound path and method through:

```properties
mosip.service.end-points=/**
# The adapter's default is GET only; without this every POST gets the adapter's 401 (KER-ATH-*).
mosip.service.exclude.auth.allowed.method=GET,POST,PUT,PATCH,DELETE
```

App requests are authenticated by `EnrolmentSessionFilter` instead. Because nothing else protects them, actuator exposes only `info` and `health`.

## Build and test

```bash
cd id-repository
mvn install -pl id-repository-core -Dmaven.test.skip=true -Dgpg.skip=true
mvn test -pl id-repository-paas-service -Dgpg.skip=true
```

Tests need no database, config server or auth adapter.

## Data protection

- Passport images are never stored.
- Passport numbers are only kept as an HMAC (key from keymanager).
- No personal data in logs: DTOs holding personal data have redacted `toString()`.

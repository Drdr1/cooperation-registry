# cooperation-registry

Backend for an internal information system that tracks **international meetings, agreements and their implementation**: who is responsible, what is due when, what is overdue. It provides role-based access, a tamper-proof audit trail, notifications, Excel/PDF reports, and adapters for national SSO (OneID) and an external government registry (SOAP).

**Stack:** Java 21 · Spring Boot 3.5 · PostgreSQL 16 · deployed as a WAR on **Apache Tomcat 10.1** behind **Nginx** on **Ubuntu Server 24.04**. Interface languages: **Uzbek, Russian, English**.

> This is a work sample with fictional demo data. The OneID and external-registry adapters run against mocks; the real contracts plug into the same interfaces.

## Scope covered

| Requirement | Implementation |
|---|---|
| Data model & database | PostgreSQL 16, schema owned by Flyway migrations; Hibernate only validates it (`ddl-auto: validate`) |
| REST API | Spring MVC, JSON, RFC 7807 problem details with localized messages, paging, filtering, sorting |
| SOAP where required | Spring-WS endpoint `/ws` (WSDL generated from XSD) for other government systems; HTTP Basic integration account |
| Role-based access control | Fine-grained permissions grouped into admin-editable roles; method-level checks; ownership rule for executors |
| Audit log | Every change with field-level diff, user, IP and time; **append-only, enforced by a PostgreSQL trigger** |
| Meetings & agreements registry | Statuses with an explicit life cycle, deadlines, responsible persons, status history, optimistic locking |
| Notifications | In-app inbox (per-user language) plus pluggable delivery channel; daily deadline job, idempotent |
| Reports | Agreements register and implementation status, **Excel (Apache POI) and PDF (OpenPDF, embedded Cyrillic font)** |
| Administration | Users, roles/permissions, reference books (countries, organizations, agreement types, meeting formats) |
| OneID integration | OAuth2 authorization-code adapter + mock; signed `state`; SSO never auto-creates accounts |
| External government system | SOAP client adapter (Spring-WS) + mock; result stored on the agreement and audited |
| API docs, tests, deployment | OpenAPI 3 / Swagger UI, 60+ tests on real PostgreSQL, CI deploys the WAR to Tomcat 10.1 and smoke-tests it, [deployment guide](docs/DEPLOYMENT.md) |

## Run it

```bash
docker compose up --build
```

This starts PostgreSQL 16, the WAR on Tomcat 10.1 and Nginx, all on <http://localhost:8080>.

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- WSDL: <http://localhost:8080/ws/agreements.wsdl> (user `integration`, password `integration-demo`)

Demo accounts (password `Demo2026!pass` for all):

| User | Role | Can |
|---|---|---|
| `admin` | ADMIN | everything, including sign-off (`AGREEMENT_APPROVE`), users, roles, audit |
| `manager` | MANAGER | create and edit meetings, agreements and items; export reports |
| `executor1`, `executor2` | EXECUTOR | read; report progress on **their own** items only |
| `viewer` | VIEWER | read; export reports |

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"manager","password":"Demo2026!pass"}' | jq -r .accessToken)
curl -s localhost:8080/api/dashboard -H "Authorization: Bearer $TOKEN" -H 'Accept-Language: ru' | jq
curl -s "localhost:8080/api/reports/agreements?format=PDF" -H "Authorization: Bearer $TOKEN" -o agreements.pdf
```

## API at a glance

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/login` · `GET /api/auth/oneid/authorize` · `POST /api/auth/oneid/callback` · `GET /api/auth/me` |
| Meetings | `GET/POST /api/meetings` · `GET/PUT/DELETE /api/meetings/{id}` |
| Agreements | `GET/POST /api/agreements` · `GET/PUT /api/agreements/{id}` · `POST /api/agreements/{id}/status` · `POST /api/agreements/{id}/external-check` |
| Implementation | `POST /api/agreements/{id}/items` · `PUT/DELETE /api/items/{id}` · `POST /api/items/{id}/progress` · `GET /api/items/mine` |
| Notifications | `GET /api/notifications` · `GET /api/notifications/unread-count` · `POST /api/notifications/{id}/read` · `POST /api/notifications/read-all` |
| Reports | `GET /api/reports/agreements?format=XLSX\|PDF&…filters` · `GET /api/reports/implementation?format=…` · `GET /api/dashboard` |
| Administration | `/api/admin/users` · `/api/admin/roles` · `/api/references/{COUNTRY\|ORGANIZATION\|AGREEMENT_TYPE\|MEETING_FORMAT}` · `GET /api/audit` · `POST /api/admin/jobs/deadline-reminders` |
| SOAP | `POST /ws`, operation `GetAgreementStatus` |

The frontend sends `Accept-Language: uz | ru | en`. Labels, validation and error messages, notifications and report headers all follow it. Reference books store all three names.

## Design decisions

**Agreement life cycle.** `DRAFT → UNDER_REVIEW → SIGNED → IN_FORCE ⇄ SUSPENDED → EXPIRED`, with `TERMINATED` reachable from any open state. The transition table in `AgreementStatus` is the single source of truth:
- The API rejects other moves with `409`.
- Each response lists the allowed `nextStatuses`, so the UI never offers an illegal button.
- Signing requires `AGREEMENT_APPROVE`; terminating requires a reason.
- Agreements past `validUntil` are expired automatically by the daily job.

**Permissions, not role names.** The code checks only permissions (`AGREEMENT_WRITE`, `REPORT_EXPORT`, …). Roles are bundles that an administrator can edit. Permissions are **re-read from the database on every request**, so deactivating a user or changing a role takes effect immediately, not when the token expires (tested). Executors may update only the implementation items they are responsible for.

**Audit.** Services write the audit entry in the same transaction as the change, so the change and its record commit or roll back together. Entries store a field-level diff (`{"title": {"from": …, "to": …}}`). A database trigger rejects `UPDATE`/`DELETE` on `audit_log`, so even the application's own DB account cannot rewrite history. Logins (including failures), exports and SOAP calls are audited too.

**Concurrency.** Every editable record has a version. Clients send back the version they loaded; a stale version gets `409`, so one user can't silently overwrite another's changes.

**Login security.**
- BCrypt password hashes.
- Lockout after 5 failed attempts.
- Identical answers for an unknown user and a wrong password.
- No default credentials: the first admin comes from `BOOTSTRAP_ADMIN_PASSWORD`.

**OneID.** Standard OAuth2 authorization-code flow:
1. The backend issues a signed, 10-minute `state`.
2. The frontend redirects the browser to OneID.
3. The backend exchanges the code and reads userinfo.
4. The OneID identity must already be linked to an account by an administrator; SSO never creates users.

Endpoint URLs and attribute names are configuration, so the real OneID values from the specification drop in without code changes.

**Notifications.** Messages are stored as a key plus arguments and rendered in the reader's language. The daily job is idempotent: each reminder has a dedupe key, so re-running it sends nothing twice. Delivery beyond the in-app inbox (e-mail, SMS, messenger) is a `NotificationChannel` bean.

**Reports.** Each report is built once as a format-neutral table, then rendered to Excel or PDF. The PDF embeds DejaVu Sans because the standard PDF fonts have no Cyrillic glyphs, and Uzbek needs `ʻ`.

## Tests

```bash
docker compose up -d db          # PostgreSQL 16 on :5432 (or point DB_URL at your own)
mvn verify
```

Integration tests run the full application against real PostgreSQL with the Flyway schema and demo data; each test rolls back. They cover:
- **Auth:** lockout, token revocation on deactivation, OneID flow including forged state.
- **Agreements:** the life cycle and approval rule, optimistic locking, field-level audit diffs.
- **Implementation items:** the executor ownership rule.
- **Notifications:** localized rendering, idempotency of the deadline job, automatic expiry.
- **Reports and admin:** Excel/PDF content, role edits applying to live tokens, immutability of the audit table.
- **SOAP:** the endpoint and its authentication.

Unit tests cover the real OneID and SOAP client adapters against stubbed remote services.

CI (`.github/workflows/ci.yml`) also deploys the WAR on **Tomcat 10.1 behind Nginx** in Docker and smoke-tests login, the dashboard, PDF export and a raw SOAP call.

## Project layout

```
src/main/java/dev/derops/registry/
  agreement/      agreements, life cycle, implementation items
  meeting/        meetings
  notification/   inbox, delivery channel, daily deadline job
  report/         dashboard, report tables, Excel/PDF renderers
  audit/          append-only audit trail
  auth/           login, JWT, OneID adapter (HTTP + mock), bootstrap admin
  user/           users, roles, permissions
  reference/      reference books
  integration/    SOAP endpoint (soap/) and external registry client (external/)
src/main/resources/
  db/migration/   Flyway schema    db/demo/  demo data (demo/test profiles only)
  messages_*.properties   uz / ru / en
deploy/nginx/     Nginx site config    docs/DEPLOYMENT.md   Ubuntu 24.04 install guide
```

## Known limits of the sample

- **Mocked integrations.** OneID and the external registry are mocked. The real contracts (OneID endpoints and attributes, the registry's WSDL) come from their official specifications and slot into `HttpOneIdClient` and `SoapExternalRegistryClient`.
- **Translations.** The Uzbek and Russian strings are drafts for a native speaker to review.
- **Single Tomcat node.** The scheduled job assumes one node; with several, add a distributed lock (e.g. ShedLock).
- **Support window.** Spring Boot 3.5 is the last line on Tomcat 10.1. Moving to Boot 4 later means Tomcat 11.

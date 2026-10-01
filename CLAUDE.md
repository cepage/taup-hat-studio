# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

TaupHat Studio is the CMS for tauphat.com, a digital artist's website. It manages webcomics (series → issues → pages), a portfolio (items and ordered sets), and site theming. It generates a static site that it deploys to Firebase Hosting, and it delivers commission requests submitted through that site. `README.md` covers features, env vars, and deployment in more detail.

- **Backend**: Spring Boot 4.1 / Java 21, base package `org.tanzu.thstudio`, Package-by-Feature
- **Frontend (CMS)**: Angular 21.1 in `src/main/frontend/`, standalone + zoneless + signals, Material Design 3
- **Published site**: Thymeleaf templates → plain HTML/CSS/vanilla JS (no framework, no bundler)
- **Data**: PostgreSQL on Cloud Foundry (`th-db`), H2 locally; Flyway migrations in `src/main/resources/db/migration/`
- **Google Cloud** (project `cf-mcp`): Cloud Storage (images, fonts, backups), Firebase Hosting, Firestore (commission queue)

## Commands

```bash
# Full build: installs Node into target/, runs npm ci + ng build, packages one JAR
./mvnw clean package

# Backend only (skip the frontend steps, much faster)
./mvnw test -Dskip.installnodenpm -Dskip.npm
./mvnw test -Dskip.installnodenpm -Dskip.npm -Dtest=CommissionMailServiceTest
./mvnw test -Dskip.installnodenpm -Dskip.npm -Dtest='CommissionMailServiceTest#failedSendsAreRetriedUntilMaxAttempts'

# Local dev: two terminals, then open http://localhost:4200
./mvnw spring-boot:run -Dskip.installnodenpm -Dskip.npm -Dspring-boot.run.profiles=local
cd src/main/frontend && npm start        # proxy.conf.json forwards /api, /oauth2, /login to :8080

# Frontend (in src/main/frontend/): Vitest + happy-dom via the Angular builder
npm test
npx ng test --include src/app/app.spec.ts
npm run build

# Deploy (vars.yaml is git-ignored and holds secrets; never print its contents)
./mvnw clean package && cf push --vars-file vars.yaml
```

Prettier is configured with a 100-character line width and single quotes. There is no separate lint step. After the Vitest summary, `ng test` prints a `socket hang up`/`ECONNREFUSED` error even when every test passes; go by the "Tests N passed" line.

## Architecture

### Profiles and auth
- `local`: H2 in-memory DB, `tauphat.security.local-mode=true` (every endpoint is open, and `AuthController` reports a fake "Local Developer" user). Tests use `@ActiveProfiles("local")`.
- `cloud`: activated automatically on Cloud Foundry. Postgres comes from VCAP_SERVICES (java-cfenv). Google OAuth2 is restricted to `TAUPHAT_ALLOWED_EMAILS`. `/api/**` requires authentication, `/actuator/health` is public.
- `GoogleCredentialsInitializer` writes `GOOGLE_CREDENTIALS_JSON` to the path in `GOOGLE_APPLICATION_CREDENTIALS` at startup, because CF can only inject env vars. Every Google call then uses Application Default Credentials.
- App configuration is the `TaupHatProperties` record (`tauphat.*`). Feature-specific records (`BackupProperties`, `CommissionProperties`) are registered in their feature's `@Configuration`.

### Calling Google services
Firebase Hosting (`publish/FirebaseHostingService`) and Firestore (`commission/FirestoreMailQueue`) are called over their **REST APIs** with `java.net.http.HttpClient` + `GoogleCredentials.getApplicationDefault().createScoped(...)`, not through Google client libraries. This keeps the memory footprint down (the container is tuned tightly, see below). Only GCS uses a client library (`image/StorageService`). Follow the REST pattern for new Google integrations.

### Static site generation (`publish`)
- `SiteGeneratorService` pulls content from the repositories and builds an in-memory `GeneratedSite`. `SiteRendererService` renders each template with a dedicated `siteTemplateEngine` (`SiteGeneratorConfig`). Spring MVC Thymeleaf is disabled, because the CMS UI is the Angular SPA.
- Shared template variables (theme colors/fonts, `commissionsEmail`, Firebase web config, reCAPTCHA key, analytics ID) come from `SiteRendererService.baseContext()`. `style.css.html` is rendered in TEXT mode into `/css/style.css`.
- **Files in `src/main/resources/site-assets/` are not picked up automatically.** Each one is registered explicitly in `SiteGeneratorService` (`site.addJs(...)` / `site.addBinary(...)`).
- Publishing deploys either to a Firebase preview channel or to live. There is no `firebase.json`; any Hosting config (headers etc.) belongs in the version config sent by `FirebaseHostingService`.

### Commission requests (`commission`)
Published form (`site-assets/commissions-form.js`, Firebase JS SDK + App Check) → Firestore `mail` collection → `CommissionScheduler` (every minute, only when `tauphat.commissions.enabled`, which is true only in `cloud`) → `CommissionMailService`:
- `ingest()` saves each doc to `commission_request` (unique `firestore_id`) and then **deletes it from Firestore**. Firestore is only a queue; Postgres is the record. (Firestore can't query for a missing field, so keeping docs and polling for undelivered ones would read the whole collection every time.)
- `dispatch()` sends PENDING/FAILED rows through Gmail SMTP (`spring.mail.*`, `MAIL_USERNAME`/`MAIL_PASSWORD`), up to `max-attempts`. The recipient is always `SiteConfig.commissionsEmail`, never the doc's `to` field. Reply-To is set only when the visitor's contact is a valid email address.
- The CMS **Commissions** page (`frontend/.../commissions/`) lists requests and supports reply, retry, and delete.
- The owner address is also pinned in the Firestore security rules (deployed manually; not in this repo) and baked into the published page, and the rules reject any other `to`. Changing `commissionsEmail` therefore requires updating the rules and republishing.
- `management.health.mail.enabled=false` keeps SMTP out of `/actuator/health`, which is the CF health check. Don't re-enable it.

### Scheduling and backups
- `@EnableScheduling` is declared once, on `backup/BackupConfig`.
- `BackupScheduler` dumps the tables listed in `SqlDumpGenerator.TABLE_ORDER` to `gs://<bucket>/backups/*.sql.gz` weekly and keeps the newest 3. Manual run: `POST /api/backup`. **Add every new table to `TABLE_ORDER`** in foreign-key-safe order.

### Persistence
- `spring.jpa.hibernate.ddl-auto=validate`: every entity change needs a new `V<n>__*.sql` Flyway migration that works on both H2 (PostgreSQL mode) and Postgres. The `contextLoads` test catches entity/schema mismatches.
- Timestamps are `TIMESTAMP WITH TIME ZONE` mapped to `Instant`.

### Image pipeline (`image`)
`ImageProcessingService` (Thumbnailator + TwelveMonkeys for WebP) produces `original`, `optimized` (≤1200px, 85% quality), and `thumbnail` (≤300px) versions. All three are stored in GCS, and their URLs and dimensions are saved on the entity.

### Frontend conventions
- Each feature folder has `*.models.ts`, a `*.service.ts` (HttpClient against `/api/...`), and standalone components that load lazily from `app.routes.ts`. Sidebar nav items are the `navItems` signal in `app.ts`.
- Use `inject()`, signals for component state, and `@if`/`@for` control flow. Style with Material 3 system tokens (`var(--mat-sys-*)`).
- Reuse `shared/confirm-dialog` (destructive actions) and `shared/empty-state`.

## Gotchas

- Spring Boot 4 ships **Jackson 3**: use `tools.jackson.databind.ObjectMapper`/`JsonNode`. Annotations such as `@JsonIgnore` are still `com.fasterxml.jackson.annotation`.
- In tests, use `@MockitoBean` (from `org.springframework.test.context.bean.override.mockito`); `@MockBean` no longer exists.
- Memory is tuned for a 1536M container: `server.tomcat.threads.max=20` in `application-cloud.properties` must stay consistent with `memory_calculator.stack_threads: 80` in `manifest.yml`.
- New `manifest.yml` `((placeholders))` need matching keys in `vars.yaml`, or `cf push` fails.
- Use the installed `playwright-cli` for browser checks of the CMS (`playwright-cli open http://localhost:4200/...`).

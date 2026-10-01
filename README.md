# TaupHat Studio

A content management system for [tauphat.com](https://tauphat.com/) — a personal website featuring webcomic publications, a professional art portfolio, commissions, and a web store. TaupHat Studio lets the site owner manage all content and customize the site's visual identity without writing code, publish a static site to Firebase Hosting with the click of a button, and receive commission requests submitted through that site.

## Features

- **Webcomic Management** — Organize comics into series and issues, upload pages with drag-and-drop reordering, automatic image processing (original, optimized, thumbnail)
- **Portfolio Management** — Upload artwork with metadata, group it into ordered portfolio sets, reorder, and replace images
- **Site Theming** — Customize colors, fonts (Google, Adobe, or uploaded custom fonts), hero image, taglines, about page content, social links, and Google Analytics
- **Static Site Generation** — Thymeleaf-powered templates produce a fast, framework-free static site (plain HTML/CSS/JS)
- **One-Click Publishing** — Deploy directly to Firebase Hosting via the REST API, with preview channels for staging
- **Commission Requests** — The public site's commission form queues requests in Firestore; the CMS delivers them by email and keeps them in a **Commissions** inbox (reply, retry failed delivery, delete)
- **Scheduled Backups** — Weekly gzipped SQL dumps of the database to Cloud Storage

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                       Cloud Foundry                          │
│  ┌──────────────────┐    ┌───────────────────┐               │
│  │  Angular CMS UI  │───▶│  Spring Boot API  │               │
│  └──────────────────┘    └──┬──────────┬─────┘               │
│                             │          │                     │
│                    ┌────────▼───────┐  │                     │
│                    │   PostgreSQL   │  │                     │
│                    │  (CF Service)  │  │                     │
│                    └────────────────┘  │                     │
└────────────────────────────────────────┼─────────────────────┘
                                         │
              ┌──────────────────────────▼───────────────────────┐
              │                Google Cloud / Firebase           │
              │                                                  │
              │  Cloud Storage ── images, fonts, DB backups      │
              │  Firebase Hosting ── published static site       │
              │  Firestore "mail" ── commission request queue    │
              │  Gmail SMTP ── commission email delivery         │
              └──────────────────────────▲───────────────────────┘
                                         │ addDoc (App Check + rules)
                              ┌──────────┴──────────┐
                              │  tauphat.com visitor │
                              │  (commissions form)  │
                              └─────────────────────┘
```

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Backend | Spring Boot | 4.1.0 |
| Language | Java | 21 |
| Frontend (CMS) | Angular + Material Design 3 (zoneless, signals) | 21.1 |
| Frontend (Published Site) | Plain HTML/CSS/JS | — |
| Database | PostgreSQL (Cloud Foundry) / H2 (local) + Flyway | — |
| Image Storage | Google Cloud Storage | 2.49.0 |
| Static Hosting | Firebase Hosting | — |
| Commission Queue | Cloud Firestore + App Check (reCAPTCHA v3) | — |
| Email | Spring Mail via Gmail SMTP | — |
| Image Processing | Thumbnailator + TwelveMonkeys WebP | 0.4.20 |
| Build System | Maven + Frontend Maven Plugin | — |
| Auth | Google OAuth2 | — |

## Prerequisites

- **Java 21+**
- **Maven 3.9+** (or use the included `mvnw` wrapper)
- **Node.js 25+** and npm (auto-installed by Frontend Maven Plugin during build)

## Local Development

Start two terminals — one for the backend and one for the frontend:

```bash
# Terminal 1: Spring Boot with H2 database, no OAuth required
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Terminal 2: Angular dev server with proxy to Spring Boot
cd src/main/frontend && npm start
```

Open [http://localhost:4200](http://localhost:4200) to access the CMS.

The `local` profile uses an H2 in-memory database and disables OAuth authentication for development convenience. The commission email job is disabled locally (`tauphat.commissions.enabled=false`), since it needs GCP credentials and an SMTP password.

## Build

Build the full application (backend + frontend) into a single JAR:

```bash
./mvnw clean package
```

The Maven build automatically installs Node.js, runs `npm ci`, executes `ng build`, and packages the Angular output into the Spring Boot JAR as static resources.

## Testing

```bash
# Spring Boot tests (H2 + local-mode security)
./mvnw test

# Angular tests (Vitest)
cd src/main/frontend && npm test
```

## Deployment

### Cloud Foundry

The application deploys to Cloud Foundry with a bound PostgreSQL service (`th-db`). Values for the `((placeholders))` in `manifest.yml` live in a git-ignored `vars.yaml`:

```bash
./mvnw clean package
cf push --vars-file vars.yaml
```

Environment variables set by `manifest.yml`:

| Variable | Purpose |
|----------|---------|
| `GCS_BUCKET_NAME` | Cloud Storage bucket for images, fonts, and backups |
| `GCS_PROJECT_ID` | GCP project ID |
| `FIREBASE_SITE_ID` | Firebase Hosting site ID |
| `FIREBASE_PROJECT_ID` | Firebase project (also the Firestore project the CMS reads from) |
| `FIREBASE_API_KEY`, `FIREBASE_AUTH_DOMAIN`, `FIREBASE_STORAGE_BUCKET`, `FIREBASE_MESSAGING_SENDER_ID`, `FIREBASE_APP_ID` | Firebase web app config, baked into the published site (public by design) |
| `RECAPTCHA_SITE_KEY` | reCAPTCHA v3 site key for App Check on the published site |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Gmail address and app password used to send commission emails |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Google OAuth2 credentials for CMS login |
| `TAUPHAT_ALLOWED_EMAILS` | Email addresses allowed to sign in to the CMS |
| `GOOGLE_CREDENTIALS_JSON`, `GOOGLE_APPLICATION_CREDENTIALS` | GCP service account JSON, and the path it is written to at startup |

The service account needs Storage Admin (images/backups), Firebase Hosting access (publishing), and **Cloud Datastore User** (reading and deleting the Firestore `mail` queue).

### Published Site

The CMS generates and deploys the public site directly to Firebase Hosting via the REST API — no Cloud Build, no staging bucket, no `firebase.json` required. Use the **Publish** page in the CMS to:

1. **Preview** — Deploy to a temporary Firebase preview channel for staging review
2. **Deploy to Production** — Publish to the live site

| Template (`site-templates/`) | Output |
|---|---|
| `home.html` | `/index.html` |
| `series-list.html`, `series-detail.html`, `issue-reader.html` | `/comics/...` |
| `portfolio.html`, `portfolio-set.html` | `/portfolio/...` |
| `about.html` | `/about/index.html` |
| `commissions.html` | `/commissions/index.html` |
| `style.css.html` | `/css/style.css` (theme colors and fonts injected from `SiteConfig`) |

Interactivity is hand-written vanilla JS in `site-assets/` (comic reader, PhotoSwipe lightbox, set viewer, carousel, commission form), with PhotoSwipe and AOS loaded from CDNs. There is no bundler or build step for the published output.

## Commission Requests

```
visitor form ──addDoc──▶ Firestore "mail" ──poll (1 min)──▶ CMS ──▶ Postgres commission_request
                                         ◀──── delete ─────        │
                                                                    └──SMTP──▶ commissions email
```

1. `commissions-form.js` on the published site writes `{ to, replyTo, message: { subject, text } }` to the Firestore `mail` collection. **App Check** (reCAPTCHA v3, enforced) and **Firestore security rules** restrict clients to creating well-formed docs addressed to the pinned owner address.
2. A scheduled job in the CMS (`commission` package) lists the `mail` collection over the Firestore REST API, saves each doc to the `commission_request` table, and deletes it from Firestore, so Firestore holds only undelivered requests.
3. Pending requests are emailed through Gmail SMTP to the **Commissions Email** set in the Theme editor (never the address in the doc), with Reply-To set to the visitor when their contact is an email address. Failed sends are retried up to 5 times.
4. The CMS **Commissions** page lists every request with its delivery status, and supports reply, retry, and delete.

If the CMS is down, requests wait in Firestore and are delivered when it comes back.

> **Note:** The owner address is pinned in the Firestore rules and also baked into the published commissions page. If **Commissions Email** changes, update the rules and republish, otherwise form submissions are rejected.

## Backups

`BackupScheduler` writes a gzipped SQL dump of all tables to `gs://$GCS_BUCKET_NAME/backups/` every Sunday at 02:00 UTC (`tauphat.backup.schedule`) and keeps the most recent 3 (`tauphat.backup.retention-count`). Trigger one manually with `POST /api/backup`.

## Project Structure

```
src/main/
├── java/org/tanzu/thstudio/
│   ├── config/          # Security, OAuth2, app properties, GCP credentials bootstrap
│   ├── image/           # GCS storage and image processing (resize/thumbnail)
│   ├── site/            # Site configuration (theming, metadata)
│   ├── webcomic/        # Webcomic series, issues, and pages
│   ├── portfolio/       # Portfolio items and sets
│   ├── commission/      # Firestore queue drain, email delivery, commissions inbox API
│   ├── backup/          # Scheduled database backups to GCS
│   └── publish/         # Static site generator and Firebase Hosting deployment
├── frontend/            # Angular 21 CMS application
│   └── src/app/
│       ├── auth/        # Authentication service and guard
│       ├── dashboard/   # CMS dashboard
│       ├── webcomic/    # Webcomic management UI
│       ├── portfolio/   # Portfolio management UI
│       ├── commissions/ # Commission request inbox
│       ├── theme/       # Theme editor
│       ├── publish/     # Publish/deploy UI
│       └── shared/      # Confirm dialog, empty state
└── resources/
    ├── db/migration/    # Flyway database migrations
    ├── site-templates/  # Thymeleaf templates for static site generation
    └── site-assets/     # Vanilla JS and images for the published site
```

## License

Private project. All rights reserved.

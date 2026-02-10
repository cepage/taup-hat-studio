# TaupHat Studio

A content management system for [tauphat.com](https://tauphat.com/) — a personal website featuring webcomic publications, a professional art portfolio, and a web store. TaupHat Studio lets the site owner manage all content and customize the site's visual identity without writing code, then publish a static site to Firebase Hosting with the click of a button.

## Features

- **Webcomic Management** — Organize comics into series and issues, upload pages with drag-and-drop reordering, automatic image processing (original, optimized, thumbnail)
- **Portfolio Management** — Upload artwork with metadata, categorize, reorder, and replace images
- **Site Theming** — Customize colors, fonts, hero image, about page content, and social links with live preview
- **Static Site Generation** — Thymeleaf-powered templates produce a fast, framework-free static site (plain HTML/CSS/JS)
- **One-Click Publishing** — Deploy directly to Firebase Hosting via the REST API, with preview channels for staging

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                  Cloud Foundry                              │
│  ┌──────────────────┐    ┌──────────────────┐              │
│  │  Angular CMS UI  │───▶│  Spring Boot API │              │
│  └──────────────────┘    └────────┬─────────┘              │
│                                   │                         │
│                          ┌────────▼─────────┐              │
│                          │   PostgreSQL      │              │
│                          │  (CF Service)     │              │
│                          └──────────────────┘              │
└───────────────────────────────┬─────────────────────────────┘
                                │
                    ┌───────────▼───────────┐
                    │  Google Cloud Platform │
                    │                       │
                    │  ┌─────────────────┐  │
                    │  │  Cloud Storage  │  │
                    │  │  (images/assets)│  │
                    │  └─────────────────┘  │
                    │                       │
                    │  ┌─────────────────┐  │
                    │  │ Firebase Hosting│  │
                    │  │ (static site)   │  │
                    │  └─────────────────┘  │
                    └───────────────────────┘
```

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Backend | Spring Boot | 4.0.2 |
| Language | Java | 21 |
| Frontend (CMS) | Angular + Material Design 3 | 21.1 |
| Frontend (Published Site) | Plain HTML/CSS/JS | — |
| Database | PostgreSQL (Cloud Foundry) / H2 (local) | — |
| Image Storage | Google Cloud Storage | — |
| Static Hosting | Firebase Hosting | — |
| Image Processing | Thumbnailator | 0.4.20 |
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

The `local` profile uses an H2 in-memory database and disables OAuth authentication for development convenience.

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

# Angular tests
cd src/main/frontend && npm test
```

## Deployment

### Cloud Foundry

The application deploys to Cloud Foundry with a bound PostgreSQL service:

```bash
./mvnw clean package
cf push
```

See `manifest.yml` for the deployment configuration. Required environment variables:

| Variable | Purpose |
|----------|---------|
| `GCS_BUCKET_NAME` | Google Cloud Storage bucket for images |
| `GCS_PROJECT_ID` | GCP project ID |
| `FIREBASE_SITE_ID` | Firebase Hosting site ID |
| `GOOGLE_CLIENT_ID` | Google OAuth2 client ID |
| `GOOGLE_CLIENT_SECRET` | Google OAuth2 client secret |
| `TAUPHAT_ALLOWED_EMAILS` | Authorized email address for CMS access |
| `GOOGLE_APPLICATION_CREDENTIALS` | Path to GCP service account credentials |

### Published Site

The CMS generates and deploys the public site directly to Firebase Hosting via the REST API — no Cloud Build, no staging bucket, no `firebase.json` required. Use the **Publish** page in the CMS to:

1. **Preview** — Deploy to a temporary Firebase preview channel for staging review
2. **Deploy to Production** — Publish to the live site

## Project Structure

```
src/main/
├── java/org/tanzu/thstudio/
│   ├── config/          # Security, OAuth2, app properties, web config
│   ├── image/           # GCS storage and image processing (resize/thumbnail)
│   ├── site/            # Site configuration (theming, metadata)
│   ├── webcomic/        # Webcomic series, issues, and pages
│   ├── portfolio/       # Portfolio items
│   └── publish/         # Static site generator and Firebase deployment
├── frontend/            # Angular 21 CMS application
│   └── src/app/
│       ├── auth/        # Authentication service and guard
│       ├── dashboard/   # CMS dashboard
│       ├── webcomic/    # Webcomic management UI
│       ├── portfolio/   # Portfolio management UI
│       ├── theme/       # Theme editor
│       └── publish/     # Publish/deploy UI
└── resources/
    ├── db/migration/    # Flyway database migrations
    ├── site-templates/  # Thymeleaf templates for static site generation
    └── site-assets/     # Vanilla JS for comic reader and portfolio lightbox
```

## License

Private project. All rights reserved.

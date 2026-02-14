# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

TaupHat Studio is a content management system for tauphat.com, the website of a digital artist. It manages:
- **Webcomic publications** (series, issues, pages)
- **Portfolio** of artwork
- **Site theming** (colors, fonts, images)
- **Static site generation** and deployment to Firebase Hosting

The CMS is built with:
- **Backend**: Spring Boot 4.0.2 with Java 21
- **Frontend**: Angular 21.1 with Material Design 3
- **Database**: PostgreSQL (via Cloud Foundry service) / H2 for local dev
- **Storage**: Google Cloud Storage for images and static assets
- **Build System**: Maven with Frontend Maven Plugin integration

## Architecture

### Backend (Spring Boot)
- Main application: `src/main/java/org/tanzu/thstudio/TaupHatStudioApplication.java`
- Base package: `org.tanzu.thstudio`
- **Package-by-Feature structure**:
  - `config` – Security (Google OAuth2), web config, app properties
  - `webcomic` – Series, Issue, Page entities, repositories, controllers
  - `portfolio` – PortfolioItem entity, repository, controller
  - `site` – SiteConfig entity, repository, service, controller
  - `image` – StorageService (GCS), ImageProcessingService (Thumbnailator)
  - `publish` – Static site generator, Firebase Hosting deployment (REST API)
- Database migrations: `src/main/resources/db/migration/` (Flyway)
- Application properties: `src/main/resources/application.properties`
- Profiles: `local` (H2 + no auth), `cloud` (Postgres + OAuth), `test`

### Frontend (Angular)
- Location: `src/main/frontend/`
- Angular 21.1 with standalone components (no NgModules)
- Zoneless change detection (no Zone.js dependency)
- Material Design 3 theming with Azure primary palette
- Uses SCSS for styling
- Proxy config: `proxy.conf.json` (forwards /api, /oauth2, /login to Spring Boot)
- Key areas:
  - `auth/` – AuthService, authGuard
  - `dashboard/` – Dashboard home view
  - `webcomic/` – Webcomic management components
  - `portfolio/` – Portfolio management components
  - `theme/` – Site theme editor
  - `publish/` – Publish/deploy UI

## Development Commands

### Full Stack Development
```bash
# Build entire project (backend + frontend)
./mvnw clean package

# Run Spring Boot with local profile (H2 database, no OAuth)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Clean build
./mvnw clean
```

### Frontend Only (in src/main/frontend/)
```bash
# Install dependencies
npm ci

# Development server with proxy to Spring Boot (http://localhost:4200)
npm start

# Build frontend
npm run build

# Run tests
npm test
```

### Testing
```bash
# Run Spring Boot tests (uses local/H2 profile)
./mvnw test

# Run Angular tests (in src/main/frontend/)
ng test
```

## Key Technologies & Patterns

### Frontend
- **Angular 21.1**: Uses modern standalone components, signals, and new control flow (`@if`, `@for`)
- **Zoneless**: Uses `provideZonelessChangeDetection()` for optimal performance
- **Material Design 3**: Pre-configured with `mat.theme()` and system variables
- **Styling**: SCSS with Material 3 design tokens (e.g. `var(--mat-sys-primary)`)
- **Testing**: Vitest with happy-dom (instead of Karma/Jasmine)
- **Prettier**: Configured with 100 character line width and single quotes

### Backend
- **Java 21**: Modern Java features preferred (records, lambdas, switch expressions)
- **Spring Boot 4**: RESTful web services, JPA, Security, OAuth2
- **Package-by-Feature**: Domain-driven package organization
- **Flyway**: Database schema versioning
- **Google Cloud Storage**: Image and asset hosting
- **Thumbnailator**: Server-side image resizing (thumbnail, optimized, original)

### Authentication
- **Production**: Google OAuth2, restricted to a specific email address
- **Local dev**: All endpoints permitted (no auth required)
- Config property: `tauphat.security.local-mode=true` disables OAuth

### Cloud Foundry Deployment
- PostgreSQL service bound via VCAP_SERVICES (auto-configured by java-cfenv-boot)
- GCS credentials via environment variables or application default credentials
- `cloud` profile activated automatically on Cloud Foundry

### Image Processing Workflow
- **Upload**: Images uploaded via CMS (webcomic pages, portfolio items, site assets)
- **Processing**: Thumbnailator creates three versions:
  - `thumbnail` – Small preview (max 300px width)
  - `optimized` – Web-optimized display version (max 1200px width, 85% quality)
  - `original` – Full-resolution original
- **Storage**: All versions stored in Google Cloud Storage
- **Metadata**: Image dimensions stored in database (added in V2 migration)
- **Formats**: Supports JPEG, PNG, WebP via TwelveMonkeys ImageIO

## Build Integration

The Maven build automatically:
1. Installs Node.js v25.6.0 and npm in `target/`
2. Runs `npm ci` to install frontend dependencies
3. Executes `ng build` to create production frontend build
4. Copies frontend build output from `src/main/frontend/dist/frontend/browser/` to `target/classes/static/`
5. Packages everything into a single Spring Boot JAR

Frontend build output gets served by Spring Boot as static resources at the root path.

## Cloud Foundry Deployment

Deploy to Cloud Foundry after building the JAR:

```bash
./mvnw clean package
cf push
```

The `manifest.yml` configures:
- **PostgreSQL service**: `th-db` (auto-configured by java-cfenv-boot)
- **Memory**: 1G
- **Profile**: `cloud` (automatically activated)
- **Buildpack**: Java Buildpack with JRE 21
- **Health check**: HTTP endpoint at `/actuator/health`

Required environment variables (defined in manifest.yml with credential placeholders):
- `GCS_BUCKET_NAME`, `GCS_PROJECT_ID` – Google Cloud Storage configuration
- `FIREBASE_SITE_ID` – Firebase Hosting site identifier
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` – OAuth2 credentials
- `TAUPHAT_ALLOWED_EMAILS` – Authorized email address for CMS access
- `GOOGLE_APPLICATION_CREDENTIALS`, `GOOGLE_CREDENTIALS_JSON` – GCP service account credentials

## Static Site Generation

The publish feature generates a static site from Thymeleaf templates and deploys directly to Firebase Hosting:
- **Templates**: Located in `src/main/resources/site-templates/`
- **Assets**: Vanilla JavaScript in `src/main/resources/site-assets/` (comic reader, portfolio lightbox)
- **Output**: Framework-free HTML/CSS/JS static site
- **Deployment**: Direct to Firebase Hosting via REST API (no Cloud Build, no staging bucket)
- **Preview**: Supports Firebase preview channels for staging review before production deployment

## Code Style Preferences

- **Java**: Use records and lambdas where appropriate. Package-by-Feature.
- **Angular**: Use signals and new template control flow syntax. Standalone components only.
- **Material UI**: Follow Material Design 3 standards and system variables.
- **Formatting**: Prettier configured for consistent code style.

# TaupHat Studio -- Content Management System

## Overview

TaupHat Studio is a content management system for [tauphat.com](https://tauphat.com/), the website of artist **Aurora Page**. The site serves three purposes:

1. **Webcomic publications** -- Multiple series (currently Citrine Clan with 53 issues), each with sequential image pages
2. **Professional portfolio** -- Thumbnail gallery of artwork with full-screen browsing
3. **Web store link** -- Links to her BigCartel shop

The CMS enables Aurora to manage all content and customize the site's visual identity (colors, fonts, images) without writing code. When she's ready, she publishes the site and the CMS generates static HTML/CSS/JS files deployed to Firebase Hosting.

---

## Architecture

The system has two distinct halves:

- **TaupHat Studio (CMS)** -- A Spring Boot + Angular admin app running on Cloud Foundry
- **TaupHat Site (Published Output)** -- Static HTML/CSS/JS files deployed to Firebase Hosting

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
                    │  └────────┬────────┘  │
                    │           │            │
                    │  ┌────────▼────────┐  │
                    │  │  Cloud Build    │  │
                    │  │  (deploy pipe)  │  │
                    │  └────────┬────────┘  │
                    │           │            │
                    │  ┌────────▼────────┐  │
                    │  │ Firebase Hosting│  │
                    │  │ (static site)   │  │
                    │  └─────────────────┘  │
                    └───────────────────────┘
```

### Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Backend | Spring Boot | 4.0.2 |
| Language | Java | 21 |
| Frontend (CMS) | Angular + Material Design 3 | 21.1 |
| Frontend (Published Site) | Plain HTML/CSS/JS | -- |
| Database | PostgreSQL (Cloud Foundry service) | -- |
| Database (local dev) | H2 in-memory | -- |
| Image Storage | Google Cloud Storage | -- |
| Static Hosting | Firebase Hosting | -- |
| Deploy Pipeline | Google Cloud Build | -- |
| Build System | Maven + Frontend Maven Plugin | -- |
| Auth | Google OAuth2 | -- |

---

## Services Required

| Service | Provider | Purpose |
|---------|----------|---------|
| PostgreSQL | Cloud Foundry marketplace | CMS database (content, config, metadata) |
| Cloud Storage | Google Cloud Platform | Images (originals, thumbnails, optimized) + generated static site files |
| Firebase Hosting | Google Cloud Platform | Serve the public-facing static site with CDN |
| Cloud Build | Google Cloud Platform | Deployment pipeline: push static files to Firebase |

PostgreSQL is provided by Cloud Foundry's marketplace service (not Cloud SQL). Spring Boot auto-configures the datasource from the `VCAP_SERVICES` environment variable via `java-cfenv-boot`. For local development, H2 in-memory database is used.

No Redis is needed -- the CMS is single-user and low-traffic.

---

## Data Model

### site_config

Singleton row storing the site's visual identity and metadata.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | Auto-generated |
| site_name | VARCHAR(255) | Site display name |
| primary_color | VARCHAR(7) | Hex color (e.g. `#5C4033`) |
| secondary_color | VARCHAR(7) | Hex color |
| accent_color | VARCHAR(7) | Hex color |
| heading_font | VARCHAR(255) | Google Font name |
| body_font | VARCHAR(255) | Google Font name |
| hero_image_url | VARCHAR(1024) | GCS URL for hero image |
| about_text | TEXT | About page content |
| bigcartel_url | VARCHAR(1024) | Link to BigCartel store |
| social_links | TEXT | JSON string of social media links |

### webcomic_series

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | Auto-generated |
| title | VARCHAR(255) | Series title (e.g. "Citrine Clan") |
| slug | VARCHAR(255) UNIQUE | URL slug (e.g. "citrine-clan") |
| description | TEXT | Series description |
| cover_image_url | VARCHAR(1024) | GCS URL for cover image |
| sort_order | INT | Display ordering |
| active | BOOLEAN | Whether series is visible on the site |

### webcomic_issue

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | Auto-generated |
| series_id | BIGINT FK | References webcomic_series |
| issue_number | INT | Issue number within the series |
| title | VARCHAR(255) | Issue title |
| cover_image_url | VARCHAR(1024) | GCS URL for issue cover |
| publish_date | DATE | Publication date |
| published | BOOLEAN | Whether issue is visible on the site |
| UNIQUE | (series_id, issue_number) | |

### webcomic_page

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | Auto-generated |
| issue_id | BIGINT FK | References webcomic_issue |
| page_number | INT | Sequential page number |
| image_url | VARCHAR(1024) | GCS URL for original image |
| thumbnail_url | VARCHAR(1024) | GCS URL for 300px thumbnail |
| optimized_url | VARCHAR(1024) | GCS URL for 1200px optimized |
| UNIQUE | (issue_id, page_number) | |

### portfolio_item

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT PK | Auto-generated |
| title | VARCHAR(255) | Artwork title |
| description | TEXT | Description |
| image_url | VARCHAR(1024) | GCS URL for original image |
| thumbnail_url | VARCHAR(1024) | GCS URL for 300px thumbnail |
| optimized_url | VARCHAR(1024) | GCS URL for 1200px optimized |
| category | VARCHAR(255) | Category for filtering |
| sort_order | INT | Display ordering |

---

## Authentication

Google OAuth2 flow restricted to Aurora's email address:

- Spring Security OAuth2 client configured with Google provider
- `tauphat.security.allowed-email` config property restricts login to one email
- Angular app redirects to `/oauth2/authorization/google` if not authenticated
- Session-based auth (Spring session cookie)
- Local development mode (`tauphat.security.local-mode=true`) disables OAuth entirely

---

## Image Processing Pipeline

When an image is uploaded through the CMS:

1. **Original** stored in GCS at `{basePath}/original/{filename}`
2. **Optimized** (1200px wide, 85% quality) stored at `{basePath}/optimized/{filename}`
3. **Thumbnail** (300px wide, 85% quality) stored at `{basePath}/thumbnail/{filename}`
4. All three URLs are stored in the database record

Image processing uses the Thumbnailator library. Aspect ratio is always preserved.

---

## Generated Static Site Pages

When Aurora publishes, the CMS generates these pages using Thymeleaf templates:

| Page | URL Pattern | Description |
|------|-------------|-------------|
| Home | `/` | Landing page with latest webcomic issue, featured portfolio, store link |
| Series List | `/comics/` | All active webcomic series |
| Series Detail | `/comics/{slug}/` | Issue listing for a series (newest first) |
| Issue Reader | `/comics/{slug}/{issue}/` | Sequential page reader with prev/next navigation |
| Portfolio | `/portfolio/` | Thumbnail grid with lightbox viewer |
| About | `/about/` | Bio, contact info, social links |

The static site includes:
- Vanilla JS comic reader (prev/next page navigation, image zoom, keyboard shortcuts)
- Lightbox gallery for the portfolio (thumbnail grid, full-screen viewer)
- CSS generated from Aurora's theme choices (colors, fonts)
- No framework -- plain HTML/CSS/JS for fast loading

---

## Deployment Pipeline

A Cloud Build configuration (`cloudbuild.yaml`) that:

1. Is triggered via the Cloud Build API from the Spring Boot app when Aurora clicks "Publish"
2. Syncs generated static files from a GCS staging bucket to Firebase Hosting
3. Reports status back to the CMS

A `firebase.json` configures Firebase Hosting (rewrites, headers, caching).

---

## CMS Frontend (Angular)

The Angular admin dashboard uses Material Design 3 with these areas:

| Area | Route | Purpose |
|------|-------|---------|
| Dashboard | `/` | Quick links, site status |
| Webcomics | `/webcomics` | Series/Issue/Page CRUD, drag-and-drop page reorder |
| Portfolio | `/portfolio` | Portfolio item CRUD, drag-and-drop reorder |
| Theme | `/theme` | Color pickers, font selectors, hero image, about text |
| Publish | `/publish` | Preview, deploy button, deployment status |

---

## Implementation Phases

### Phase 1: Foundation -- COMPLETED

Database, auth, project structure, GCS integration.

### Phase 2: Webcomic Management -- PENDING

Series/Issue/Page CRUD with image upload.

### Phase 3: Portfolio Management -- PENDING

Portfolio CRUD with image upload.

### Phase 4: Site Theming -- PENDING

Color/font/image customization.

### Phase 5: Static Site Generator -- PENDING

Thymeleaf templates, HTML generation, comic reader JS, portfolio lightbox.

### Phase 6: Publish Pipeline -- PENDING

Cloud Build integration, Firebase deployment, publish UI.

### Phase 7: Public Site Polish -- PENDING

Responsive design, SEO meta tags, caching headers, final polish.

---

## Phase 1 Completed Work

### Backend Dependencies (`pom.xml`)

Updated from the starter template. Key additions:

- `spring-boot-starter-data-jpa` -- JPA/Hibernate
- `spring-boot-starter-security` + `spring-boot-starter-oauth2-client` -- Google OAuth2
- `spring-boot-starter-thymeleaf` -- Template engine (for future static site generation)
- `com.google.cloud:google-cloud-storage:2.49.0` -- GCS SDK
- `net.coobird:thumbnailator:0.4.20` -- Image resizing
- `io.pivotal.cfenv:java-cfenv-boot:3.2.0` -- Cloud Foundry VCAP_SERVICES parsing
- `org.flywaydb:flyway-core` + `flyway-database-postgresql` -- Database migrations
- `org.postgresql:postgresql` -- Postgres driver (runtime)
- `com.h2database:h2` -- H2 driver for local dev (runtime)

### Backend Files Created

```
src/main/java/org/tanzu/thstudio/
├── TaupHatStudioApplication.java          # @SpringBootApplication + @EnableConfigurationProperties
├── config/
│   ├── TaupHatProperties.java             # @ConfigurationProperties record (security + gcs settings)
│   ├── SecurityConfig.java                # Google OAuth2 with email restriction + local dev mode
│   ├── AuthController.java                # GET /api/auth/user -- returns current user info
│   └── WebConfig.java                     # SPA forwarding (non-API routes -> index.html)
├── image/
│   ├── StorageService.java                # GCS upload/delete operations
│   └── ImageProcessingService.java        # Thumbnailator resize + upload (original/optimized/thumbnail)
├── site/
│   ├── SiteConfig.java                    # JPA entity
│   ├── SiteConfigRepository.java          # Spring Data JPA repository
│   ├── SiteConfigService.java             # Get/update singleton config
│   └── SiteConfigController.java          # GET/PUT /api/site-config
├── webcomic/
│   ├── WebcomicSeries.java                # JPA entity with @OneToMany -> issues
│   ├── WebcomicSeriesRepository.java       # findBySlug, findByActive, ordered queries
│   ├── WebcomicIssue.java                 # JPA entity with @ManyToOne -> series, @OneToMany -> pages
│   ├── WebcomicIssueRepository.java        # findBySeries, findLatestPublished
│   ├── WebcomicPage.java                  # JPA entity with @ManyToOne -> issue
│   └── WebcomicPageRepository.java         # findByIssue ordered by pageNumber
└── portfolio/
    ├── PortfolioItem.java                 # JPA entity
    └── PortfolioItemRepository.java        # findByCategory, ordered queries
```

### Database Migration

```
src/main/resources/db/migration/
└── V1__initial_schema.sql                  # Creates all 5 tables, indexes, constraints, seeds default config
```

### Application Configuration

```
src/main/resources/
├── application.properties                  # Shared config (server, JPA, Flyway, OAuth2, GCS, upload limits)
├── application-local.properties            # H2 in-memory + local-mode security (no OAuth)
├── application-cloud.properties            # PostgreSQL dialect + production security
└── application-test.properties             # H2 + local-mode for @SpringBootTest
```

Key configuration properties:

| Property | Purpose |
|----------|---------|
| `tauphat.security.allowed-email` | Restrict OAuth login to this email |
| `tauphat.security.local-mode` | `true` disables OAuth (for local dev) |
| `tauphat.gcs.bucket-name` | GCS bucket for images/assets |
| `tauphat.gcs.project-id` | GCP project ID |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | OAuth2 credentials (env vars) |

### Frontend Files Created

```
src/main/frontend/
├── proxy.conf.json                         # Dev proxy: /api, /oauth2, /login -> localhost:8080
├── angular.json                            # Updated with proxyConfig for serve target
├── package.json                            # Added @angular/animations dependency
└── src/
    ├── index.html                          # Updated title to "TaupHat Studio"
    ├── main.ts                             # Bootstraps App with appConfig
    ├── styles.scss                         # Material Design 3 theme (Azure palette)
    └── app/
        ├── app.ts                          # Root component: sidenav shell with toolbar
        ├── app.html                        # Sidenav layout with nav items, user info, sign out
        ├── app.scss                        # Shell styles using Material system variables
        ├── app.config.ts                   # Providers: zoneless, router, httpClient, animations
        ├── app.routes.ts                   # Lazy-loaded routes with authGuard
        ├── auth/
        │   ├── auth.service.ts             # Signal-based auth state, login/logout methods
        │   └── auth.guard.ts               # CanActivateFn that redirects to Google OAuth
        ├── dashboard/
        │   ├── dashboard.ts                # Dashboard component with card grid
        │   ├── dashboard.html              # Quick-link cards for all management areas
        │   └── dashboard.scss              # Grid layout styles
        ├── webcomic/
        │   └── webcomic-list/
        │       └── webcomic-list.ts        # Placeholder (Phase 2)
        ├── portfolio/
        │   └── portfolio-list/
        │       └── portfolio-list.ts       # Placeholder (Phase 3)
        ├── theme/
        │   └── theme-editor/
        │       └── theme-editor.ts         # Placeholder (Phase 4)
        └── publish/
            └── publish.ts                  # Placeholder (Phase 6)
```

### Build Verification

- **Angular**: `ng build` succeeds -- produces 467 kB initial bundle + 5 lazy chunks
- **Java**: `mvnw compile` succeeds -- compiles all 19 source files
- **No lint errors** in Angular source

### How to Run Locally

```bash
# Terminal 1: Spring Boot with H2 database, no OAuth required
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Terminal 2: Angular dev server with proxy to Spring Boot
cd src/main/frontend && npm start
# Open http://localhost:4200
```

### How to Run Tests

```bash
# Spring Boot tests (uses H2 + local-mode security)
./mvnw test

# Angular tests
cd src/main/frontend && npm test
```

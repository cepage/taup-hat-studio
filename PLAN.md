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

### Phase 2: Webcomic Management -- COMPLETED

Series/Issue/Page CRUD with image upload, drag-and-drop page reordering, GCS asset lifecycle.

### Phase 3: Portfolio Management -- COMPLETED

Portfolio CRUD with image upload, image replacement, reorder, GCS asset lifecycle.

### Phase 4: Site Theming -- COMPLETED

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

---

## Phase 2 Completed Work

### Backend REST Controllers

```
src/main/java/org/tanzu/thstudio/webcomic/
├── WebcomicSeriesController.java      # CRUD + reorder for series
├── WebcomicIssueController.java       # CRUD for issues within a series
└── WebcomicPageController.java        # Page upload, reorder, delete with GCS cleanup
```

- **Series**: `GET/POST/PUT/DELETE /api/webcomic/series`, `PUT /api/webcomic/series/reorder`
- **Issues**: `GET/POST/PUT/DELETE /api/webcomic/series/{seriesId}/issues`
- **Pages**: `GET/POST /api/webcomic/series/{seriesId}/issues/{issueId}/pages`, `PUT .../pages/reorder`, `DELETE .../pages/{pageId}`

### Backend Fixes and Improvements

- **`@JsonIgnore` on lazy collections**: Added to `WebcomicSeries.issues` and `WebcomicIssue.pages` to prevent `LazyInitializationException` during JSON serialization (no open session with `spring.jpa.open-in-view=false`)
- **JPA query path resolution**: Renamed repository methods from `findBySeriesId` to `findBySeries_Id` (and `findByIssueId` to `findByIssue_Id`) for Hibernate 7.2.1 compatibility, which requires explicit underscore notation to navigate `@ManyToOne` relationships
- **WebP support**: Added `com.twelvemonkeys.imageio:imageio-webp:3.13.0` dependency so Thumbnailator can read WebP images via Java ImageIO
- **Resize output format**: Resized variants (optimized + thumbnail) are always output as PNG to avoid WebP write codec issues; originals are preserved in their native format
- **GCS asset cleanup on delete**: `StorageService.deleteByPrefix()` removes all GCS objects under a path prefix; wired into series delete (`images/webcomic/{seriesId}/`), issue delete (`images/webcomic/{seriesId}/{issueId}/`), and page delete (individual files)
- **Page reorder unique constraint**: Two-pass approach within `@Transactional` — first sets temporary negative page numbers, flushes, then assigns final order — to avoid violating the `(issue_id, page_number)` unique constraint
- **`spring-boot-starter-flyway`**: Replaced direct `flyway-core` dependency with the starter for proper auto-configuration in Spring Boot 4.x

### Frontend Components

```
src/main/frontend/src/app/
├── webcomic/
│   ├── webcomic.models.ts             # WebcomicSeries, WebcomicIssue, WebcomicPage interfaces
│   ├── webcomic.service.ts            # HTTP client for all webcomic API endpoints
│   ├── webcomic-list/                 # Series grid with create/edit/delete
│   ├── series-detail/                 # Issue list within a series
│   ├── issue-detail/                  # Page grid with upload, drag-and-drop reorder, delete
│   ├── series-dialog/                 # Create/edit series dialog
│   └── issue-dialog/                  # Create/edit issue dialog
└── shared/
    └── confirm-dialog/                # Reusable confirmation dialog
```

### Frontend Fixes and Improvements

- **CDK drag-and-drop**: Uses `cdkDropListSortingDisabled` with flex-wrap layout for reliable reordering in wrapping grids; CDK's default `mixed` sort strategy has index calculation issues with CSS Grid and flex-wrap
- **Dialog transparency fix**: Added CDK overlay styles, switched to `provideAnimationsAsync()`, and overrode neutral-derived `--mat-sys-*` CSS custom properties that `ng serve` generates as empty `light-dark(, )` values
- **Dialog label clipping**: Added `padding-top: 24px` to `mat-dialog-content` to prevent outline form field floating labels from being clipped

### Routes Added

| Route | Component | Purpose |
|-------|-----------|---------|
| `/webcomics` | `WebcomicList` | Series management grid |
| `/webcomics/:seriesId` | `SeriesDetail` | Issue management for a series |
| `/webcomics/:seriesId/issues/:issueId` | `IssueDetail` | Page management with upload and reorder |

---

## Phase 3 Completed Work

### Backend REST Controller

```
src/main/java/org/tanzu/thstudio/portfolio/
├── PortfolioItem.java                 # JPA entity (Phase 1)
├── PortfolioItemRepository.java        # Spring Data JPA repository (Phase 1)
└── PortfolioItemController.java       # Full CRUD + image upload/replace + reorder + GCS cleanup
```

- **List**: `GET /api/portfolio` -- returns all items ordered by `sortOrder`
- **Get**: `GET /api/portfolio/{id}` -- returns a single item
- **Create**: `POST /api/portfolio` (multipart) -- uploads image, creates item with title/description/category
- **Update**: `PUT /api/portfolio/{id}` -- updates metadata (title, description, category) without re-uploading
- **Replace image**: `PUT /api/portfolio/{id}/image` (multipart) -- deletes old GCS assets, uploads new image
- **Delete**: `DELETE /api/portfolio/{id}` -- deletes item and all GCS assets (original, optimized, thumbnail)
- **Reorder**: `PUT /api/portfolio/reorder` -- accepts ordered list of IDs, updates `sortOrder`

Images are stored in GCS under `images/portfolio/` using timestamp-based filenames to avoid collisions. The existing `ImageProcessingService` generates original, optimized (1200px), and thumbnail (300px) variants.

### Frontend Components

```
src/main/frontend/src/app/portfolio/
├── portfolio.models.ts                # PortfolioItem TypeScript interface
├── portfolio.service.ts               # HTTP client for all portfolio API endpoints
├── portfolio-dialog/
│   └── portfolio-dialog.ts            # Create/edit dialog with file picker + metadata fields
└── portfolio-list/
    ├── portfolio-list.ts              # Main component: grid view, signals-based state management
    ├── portfolio-list.html            # Card grid with image overlay, reorder, empty state
    └── portfolio-list.scss            # Material Design 3 styled card layout
```

### Key Features

- **Card grid layout**: Square thumbnail cards (240px) with title, category chip, and description
- **Image hover overlay**: Camera icon overlay on hover to replace an item's image without editing metadata
- **Create dialog**: Drag-zone-styled file picker with title, description, and category fields
- **Edit dialog**: Update metadata (title, description, category) without re-uploading the image
- **Reorder**: Left/right chevron buttons on each card for adjusting display order
- **Delete**: Confirmation dialog before deleting, with GCS asset cleanup
- **Empty state**: Illustrated call-to-action when no items exist
- **Upload progress**: Indeterminate progress bar during image processing
- **Signals + zoneless**: All state managed via Angular signals with `provideZonelessChangeDetection()`
- **Modern Angular**: Standalone component, `@if`/`@for` control flow, `viewChild()` signal query

---

## Phase 4 Completed Work

### Backend Changes

**`SiteConfigController.java`** -- Extended with hero image endpoints:

- **Upload hero image**: `PUT /api/site-config/hero-image` (multipart) -- processes image via `ImageProcessingService`, stores the optimized variant URL, deletes any previous hero image from GCS
- **Delete hero image**: `DELETE /api/site-config/hero-image` -- removes the hero image from GCS and clears the URL
- **Config update safety**: `heroImageUrl` is no longer overwritten by `PUT /api/site-config`; it is managed exclusively via the hero-image endpoints

**`SiteConfigService.java`** -- Added `save(SiteConfig)` method for direct entity persistence (used by the hero image endpoints).

Images are stored in GCS under `images/site/` using the existing `ImageProcessingService` pipeline (original, optimized, thumbnail). The hero image URL points to the optimized (1200px) variant.

### Frontend Components

```
src/main/frontend/src/app/theme/
├── site-config.models.ts             # SiteConfig, SocialLink interfaces + font/platform constants
├── site-config.service.ts            # HTTP client: get, update, uploadHeroImage, deleteHeroImage
└── theme-editor/
    ├── theme-editor.ts               # Main component: signal-based state, social links JSON parse/serialize
    ├── theme-editor.html             # Six-section card layout with live previews
    └── theme-editor.scss             # Material Design 3 styled editor with floating save FAB
```

### API Endpoints

| Method | URL | Purpose |
|--------|-----|---------|
| `GET` | `/api/site-config` | Returns the singleton site config (Phase 1) |
| `PUT` | `/api/site-config` | Updates all config fields except heroImageUrl (Phase 1, updated) |
| `PUT` | `/api/site-config/hero-image` | Uploads/replaces the hero image |
| `DELETE` | `/api/site-config/hero-image` | Removes the hero image |

### Theme Editor Sections

The editor is organized into six Material Design 3 outlined cards:

1. **Site Identity** -- Site name and BigCartel store URL
2. **Color Palette** -- Three native color pickers with synchronized hex text inputs and a live color preview bar (primary, secondary, accent)
3. **Typography** -- Dropdown selectors for heading and body Google Fonts with a live font preview box (15 curated heading fonts, 15 body fonts)
4. **Hero Image** -- Upload dropzone with dashed border, image preview with replace/remove buttons, progress indicator during upload
5. **About Page** -- Multi-line textarea for the about page bio content
6. **Social Links** -- Dynamic list of platform selector (13 platforms) and URL input rows with add/remove; serialized as JSON in the `socialLinks` database column

### Key Features

- **Signal-based state**: `config`, `loading`, `saving`, `uploadingHero`, `socialLinks` signals
- **Live color preview**: Color swatch bar updates in real-time as colors are changed
- **Live font preview**: Heading and body text samples render with the selected Google Font family
- **Social links JSON**: Parsed from `socialLinks` column on load, serialized back on save; empty/incomplete entries are filtered out
- **Snackbar notifications**: Success and error feedback via `MatSnackBar` for save and upload actions
- **Hero image lifecycle**: Upload processes through `ImageProcessingService` (original, optimized, thumbnail); delete cleans up all GCS assets under `images/site/`
- **Confirmation dialog**: Reuses existing `ConfirmDialog` before hero image removal
- **Floating save FAB**: Fixed-position save button in the bottom-right corner for long pages
- **Modern Angular**: Standalone component, `@if`/`@for` control flow, `FormsModule` with `ngModel` two-way binding, zoneless change detection

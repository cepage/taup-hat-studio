package org.tanzu.thstudio.publish;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tanzu.thstudio.portfolio.PortfolioItem;
import org.tanzu.thstudio.portfolio.PortfolioItemRepository;
import org.tanzu.thstudio.site.SiteConfig;
import org.tanzu.thstudio.site.SiteConfigService;
import org.tanzu.thstudio.webcomic.WebcomicIssue;
import org.tanzu.thstudio.webcomic.WebcomicIssueRepository;
import org.tanzu.thstudio.webcomic.WebcomicPage;
import org.tanzu.thstudio.webcomic.WebcomicPageRepository;
import org.tanzu.thstudio.webcomic.WebcomicSeries;
import org.tanzu.thstudio.webcomic.WebcomicSeriesRepository;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates the complete static site from CMS content.
 * Uses Thymeleaf templates to render HTML pages and produces CSS/JS assets
 * driven by the site configuration (colors, fonts, etc.).
 *
 * The generated {@link GeneratedSite} is returned in-memory for the caller
 * (typically the controller) to pass to {@link FirebaseHostingService} for deployment.
 */
@Service
@Transactional(readOnly = true)
public class SiteGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(SiteGeneratorService.class);

    private final TemplateEngine templateEngine;
    private final SiteConfigService siteConfigService;
    private final WebcomicSeriesRepository seriesRepository;
    private final WebcomicIssueRepository issueRepository;
    private final WebcomicPageRepository pageRepository;
    private final PortfolioItemRepository portfolioRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SiteGeneratorService(
            @Qualifier("siteTemplateEngine") TemplateEngine templateEngine,
            SiteConfigService siteConfigService,
            WebcomicSeriesRepository seriesRepository,
            WebcomicIssueRepository issueRepository,
            WebcomicPageRepository pageRepository,
            PortfolioItemRepository portfolioRepository) {
        this.templateEngine = templateEngine;
        this.siteConfigService = siteConfigService;
        this.seriesRepository = seriesRepository;
        this.issueRepository = issueRepository;
        this.pageRepository = pageRepository;
        this.portfolioRepository = portfolioRepository;
    }

    /**
     * Generates the full static site and returns the result without uploading.
     * Useful for preview or testing.
     */
    public GeneratedSite generate() {
        log.info("Starting static site generation...");
        var site = new GeneratedSite();
        var config = siteConfigService.getConfig();
        var activeSeries = seriesRepository.findByActiveTrueOrderBySortOrderAsc();
        var portfolioItems = portfolioRepository.findAllByOrderBySortOrderAsc();

        // Generate CSS from theme config
        site.addCss("css/style.css", generateStyleCss(config));

        // Generate JS assets
        site.addJs("js/comic-reader.js", loadStaticAsset("site-assets/comic-reader.js"));
        site.addJs("js/portfolio-lightbox.js", loadStaticAsset("site-assets/portfolio-lightbox.js"));
        site.addJs("js/stars.js", loadStaticAsset("site-assets/stars.js"));

        // Home page
        site.addHtml("index.html", renderHome(config, activeSeries, portfolioItems));

        // Comics series list
        site.addHtml("comics/index.html", renderSeriesList(config, activeSeries));

        // Each series detail + issue readers
        for (var series : activeSeries) {
            var publishedIssues = issueRepository.findBySeries_IdAndPublishedTrueOrderByIssueNumberDesc(series.getId());
            site.addHtml("comics/" + series.getSlug() + "/index.html",
                    renderSeriesDetail(config, series, publishedIssues));

            for (var issue : publishedIssues) {
                var pages = pageRepository.findByIssue_IdOrderByPageNumberAsc(issue.getId());
                site.addHtml("comics/" + series.getSlug() + "/" + issue.getIssueNumber() + "/index.html",
                        renderIssueReader(config, series, issue, pages, publishedIssues));
            }
        }

        // Portfolio page
        site.addHtml("portfolio/index.html", renderPortfolio(config, portfolioItems));

        // Commissions page
        site.addHtml("commissions/index.html", renderCommissions(config));

        // About page
        site.addHtml("about/index.html", renderAbout(config));

        log.info("Static site generation complete: {} files", site.fileCount());
        return site;
    }

    // ── Template rendering methods ──────────────────────────────────────────

    private String renderHome(SiteConfig config, List<WebcomicSeries> activeSeries,
                              List<PortfolioItem> portfolioItems) {
        var ctx = baseContext(config);
        ctx.setVariable("activeSeries", activeSeries);
        ctx.setVariable("portfolioItems", portfolioItems.stream().limit(8).toList());
        ctx.setVariable("taglines", parseTaglines(config.getSiteTaglines()));

        // Find latest issue from the first active series
        if (!activeSeries.isEmpty()) {
            var latestIssue = issueRepository
                    .findFirstBySeries_IdAndPublishedTrueOrderByIssueNumberDesc(activeSeries.getFirst().getId());
            latestIssue.ifPresent(issue -> {
                ctx.setVariable("latestIssue", issue);
                ctx.setVariable("latestIssueSeries", activeSeries.getFirst());
            });
        }

        return templateEngine.process("home", ctx);
    }

    private String renderSeriesList(SiteConfig config, List<WebcomicSeries> activeSeries) {
        var ctx = baseContext(config);
        ctx.setVariable("activeSeries", activeSeries);
        // Add issue counts per series
        for (var series : activeSeries) {
            var count = issueRepository.findBySeries_IdAndPublishedTrueOrderByIssueNumberDesc(series.getId()).size();
            ctx.setVariable("issueCount_" + series.getId(), count);
        }
        return templateEngine.process("series-list", ctx);
    }

    private String renderSeriesDetail(SiteConfig config, WebcomicSeries series,
                                       List<WebcomicIssue> publishedIssues) {
        var ctx = baseContext(config);
        ctx.setVariable("series", series);
        ctx.setVariable("issues", publishedIssues);
        return templateEngine.process("series-detail", ctx);
    }

    private String renderIssueReader(SiteConfig config, WebcomicSeries series,
                                      WebcomicIssue issue, List<WebcomicPage> pages,
                                      List<WebcomicIssue> allIssues) {
        var ctx = baseContext(config);
        ctx.setVariable("series", series);
        ctx.setVariable("issue", issue);
        ctx.setVariable("pages", pages);

        // Provide clean page data for JavaScript (Thymeleaf inline serialization)
        var pagesJson = pages.stream().map(p -> {
            var map = new LinkedHashMap<String, Object>();
            map.put("pageNumber", p.getPageNumber());
            map.put("imageUrl", p.getImageUrl());
            map.put("optimizedUrl", p.getOptimizedUrl());
            return map;
        }).toList();
        ctx.setVariable("pagesJson", pagesJson);

        // Calculate prev/next issue navigation
        int currentIdx = -1;
        for (int i = 0; i < allIssues.size(); i++) {
            if (allIssues.get(i).getId().equals(issue.getId())) {
                currentIdx = i;
                break;
            }
        }
        // allIssues is ordered newest first (DESC), so "previous" is at higher index
        if (currentIdx >= 0 && currentIdx < allIssues.size() - 1) {
            ctx.setVariable("prevIssue", allIssues.get(currentIdx + 1));
        }
        if (currentIdx > 0) {
            ctx.setVariable("nextIssue", allIssues.get(currentIdx - 1));
        }

        return templateEngine.process("issue-reader", ctx);
    }

    private String renderPortfolio(SiteConfig config, List<PortfolioItem> items) {
        var ctx = baseContext(config);
        ctx.setVariable("portfolioItems", items);
        return templateEngine.process("portfolio", ctx);
    }

    private String renderAbout(SiteConfig config) {
        var ctx = baseContext(config);
        ctx.setVariable("socialLinks", parseSocialLinks(config.getSocialLinks()));
        return templateEngine.process("about", ctx);
    }

    private String renderCommissions(SiteConfig config) {
        var ctx = baseContext(config);
        ctx.setVariable("commissionsEmail", config.getCommissionsEmail());
        return templateEngine.process("commissions", ctx);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private Context baseContext(SiteConfig config) {
        var ctx = new Context();
        ctx.setVariable("config", config);
        ctx.setVariable("siteName", config.getSiteName());
        ctx.setVariable("year", LocalDateTime.now().getYear());
        ctx.setVariable("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return ctx;
    }

    private List<Map<String, String>> parseSocialLinks(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse social links JSON: {}", e.getMessage());
            return List.of();
        }
    }

    private List<String> parseTaglines(String taglines) {
        if (taglines == null || taglines.isBlank()) {
            return List.of();
        }
        return Arrays.stream(taglines.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Generates the main stylesheet from the site configuration theme values.
     */
    private String generateStyleCss(SiteConfig config) {
        var googleFontsImport = buildGoogleFontsImport(config.getHeadingFont(), config.getBodyFont());

        var taglines = parseTaglines(config.getSiteTaglines());
        int taglineCount = Math.max(taglines.size(), 1);
        double cycleDuration = taglineCount * 3.0;

        return googleFontsImport + """
                
                /* ── Reset & Base ───────────────────────────────────────── */
                
                *, *::before, *::after {
                  box-sizing: border-box;
                  margin: 0;
                  padding: 0;
                }
                
                :root {
                  --color-primary: %s;
                  --color-secondary: %s;
                  --color-accent: %s;
                  --font-heading: '%s', serif;
                  --font-body: '%s', sans-serif;
                  --color-bg: #faf9f7;
                  --color-surface: #ffffff;
                  --color-text: #1a1a1a;
                  --color-text-muted: #6b6b6b;
                  --color-border: #e0ddd8;
                  --max-width: 1200px;
                  --radius: 8px;
                  --shadow: 0 2px 8px rgba(0,0,0,0.08);
                  --tagline-count: %d;
                  --tagline-cycle: %.1fs;
                }
                
                html {
                  font-size: 16px;
                  scroll-behavior: smooth;
                }
                
                body {
                  font-family: var(--font-body);
                  background: var(--color-bg);
                  color: var(--color-text);
                  line-height: 1.6;
                  min-height: 100vh;
                  display: flex;
                  flex-direction: column;
                  position: relative;
                  overflow-x: hidden;
                }
                
                /* ── Page Load Fade-In ──────────────────────────────────── */
                
                .page-fade-in {
                  animation: pageFadeIn 1s ease-out forwards;
                }
                
                @keyframes pageFadeIn {
                  from { opacity: 0; transform: translateY(12px); }
                  to   { opacity: 1; transform: translateY(0); }
                }
                
                a {
                  color: var(--color-primary);
                  text-decoration: none;
                  transition: color 0.2s;
                }
                a:hover {
                  color: var(--color-accent);
                }
                
                img {
                  max-width: 100%%;
                  height: auto;
                  display: block;
                }
                
                h1, h2, h3, h4 {
                  font-family: var(--font-heading);
                  line-height: 1.2;
                  color: var(--color-primary);
                }
                
                /* ── Star Decorations ───────────────────────────────────── */
                
                .stars-container {
                  position: fixed;
                  inset: 0;
                  pointer-events: none;
                  z-index: 0;
                  overflow: hidden;
                }
                
                .star-decoration {
                  position: absolute;
                  color: var(--color-primary);
                  opacity: 0;
                  animation: starTwinkle 4s ease-in-out infinite alternate;
                  transition: opacity 0.6s ease;
                }
                
                @keyframes starTwinkle {
                  0%%   { transform: scale(0.85) rotate(0deg); }
                  100%% { transform: scale(1.15) rotate(15deg); }
                }
                
                /* ── Layout ─────────────────────────────────────────────── */
                
                .site-header {
                  background: var(--color-primary);
                  padding: 0.75rem 1.5rem;
                  position: sticky;
                  top: 0;
                  z-index: 100;
                }
                
                .header-inner {
                  max-width: var(--max-width);
                  margin: 0 auto;
                  display: flex;
                  align-items: center;
                  justify-content: space-between;
                  gap: 1rem;
                }
                
                .site-logo {
                  font-family: var(--font-heading);
                  font-size: 1.5rem;
                  font-weight: 700;
                  color: #fff;
                }
                .site-logo:hover {
                  color: var(--color-secondary);
                }
                
                .site-nav {
                  display: flex;
                  gap: 1.5rem;
                  align-items: center;
                }
                
                .site-nav a {
                  font-size: 0.95rem;
                  font-weight: 500;
                  color: rgba(255,255,255,0.85);
                  padding: 0.25rem 0;
                  border-bottom: 2px solid transparent;
                  transition: border-color 0.2s, color 0.2s;
                }
                .site-nav a:hover,
                .site-nav a.active {
                  color: #fff;
                  border-bottom-color: var(--color-secondary);
                }
                
                .site-main {
                  flex: 1;
                  max-width: var(--max-width);
                  margin: 0 auto;
                  padding: 2rem 1.5rem;
                  width: 100%%;
                  position: relative;
                  z-index: 1;
                }
                
                .site-footer {
                  background: var(--color-primary);
                  padding: 1.5rem;
                  text-align: center;
                  color: rgba(255,255,255,0.7);
                  font-size: 0.85rem;
                  position: relative;
                  z-index: 1;
                }
                
                /* ── Hero Landing ───────────────────────────────────────── */
                
                .hero-landing {
                  position: relative;
                  border-radius: var(--radius);
                  overflow: hidden;
                  margin-bottom: 2.5rem;
                  background: var(--color-primary);
                  min-height: 420px;
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  text-align: center;
                }
                
                .hero-landing-bg {
                  position: absolute;
                  inset: 0;
                }
                
                .hero-landing-bg-img {
                  width: 100%%;
                  height: 100%%;
                  object-fit: cover;
                  opacity: 0.35;
                }
                
                .hero-landing-content {
                  position: relative;
                  z-index: 1;
                  padding: 3rem 2rem;
                }
                
                .hero-title {
                  color: #fff;
                  font-size: 4rem;
                  font-weight: 700;
                  letter-spacing: 0.04em;
                  text-transform: uppercase;
                  margin-bottom: 0.5rem;
                  text-shadow: 0 2px 20px rgba(0,0,0,0.3);
                }
                
                .tagline-rotator {
                  position: relative;
                  height: 2rem;
                  overflow: hidden;
                }
                
                .tagline {
                  position: absolute;
                  width: 100%%;
                  left: 0;
                  color: var(--color-secondary);
                  font-family: var(--font-heading);
                  font-size: 1.3rem;
                  font-weight: 500;
                  letter-spacing: 0.08em;
                  text-transform: uppercase;
                  opacity: 0;
                  animation: taglineCycle var(--tagline-cycle) ease-in-out infinite;
                }
                
                @keyframes taglineCycle {
                  0%%   { opacity: 0; transform: translateY(100%%); }
                  5%%   { opacity: 1; transform: translateY(0); }
                  %s%%  { opacity: 1; transform: translateY(0); }
                  %s%%  { opacity: 0; transform: translateY(-100%%); }
                  100%% { opacity: 0; }
                }
                
                /* ── Nav Buttons ─────────────────────────────────────────── */
                
                .nav-buttons {
                  display: flex;
                  justify-content: center;
                  gap: 1rem;
                  flex-wrap: wrap;
                  margin-bottom: 3rem;
                }
                
                .nav-btn {
                  display: inline-flex;
                  align-items: center;
                  justify-content: center;
                  padding: 0.75rem 2rem;
                  background: var(--color-accent);
                  color: #fff;
                  font-family: var(--font-heading);
                  font-size: 1rem;
                  font-weight: 600;
                  letter-spacing: 0.04em;
                  border-radius: var(--radius);
                  transition: background 0.2s, transform 0.2s, box-shadow 0.2s;
                  box-shadow: var(--shadow);
                }
                .nav-btn:hover {
                  background: var(--color-primary);
                  color: #fff;
                  transform: translateY(-2px);
                  box-shadow: 0 4px 16px rgba(0,0,0,0.15);
                }
                
                /* ── Home About Section ──────────────────────────────────── */
                
                .home-about {
                  display: grid;
                  grid-template-columns: 1fr 1fr;
                  gap: 3rem;
                  align-items: center;
                  margin-bottom: 3rem;
                  padding: 2rem 0;
                }
                
                .home-about-text h2 {
                  font-size: 1.75rem;
                  margin-bottom: 1rem;
                }
                
                .home-about-text p {
                  line-height: 1.8;
                  color: var(--color-text-muted);
                  margin-bottom: 1.5rem;
                  white-space: pre-wrap;
                }
                
                .about-preview-img {
                  border-radius: var(--radius);
                  box-shadow: var(--shadow);
                  width: 100%%;
                  aspect-ratio: 4/3;
                  object-fit: cover;
                }
                
                /* ── Project Circles (Home) ──────────────────────────────── */
                
                .home-projects {
                  margin-bottom: 3rem;
                }
                
                .project-circles {
                  display: flex;
                  flex-wrap: wrap;
                  justify-content: center;
                  gap: 2.5rem;
                  margin-top: 1.5rem;
                }
                
                .project-circle-item {
                  display: flex;
                  flex-direction: column;
                  align-items: center;
                  gap: 0.5rem;
                  width: 140px;
                  text-align: center;
                  text-decoration: none;
                  color: inherit;
                  cursor: pointer;
                }
                
                .project-circle-title {
                  font-family: var(--font-heading);
                  font-size: 0.9rem;
                  font-weight: 600;
                  color: var(--color-text);
                }
                
                .project-circle-img-wrap {
                  width: 120px;
                  height: 120px;
                  border-radius: 50%%;
                  overflow: hidden;
                  box-shadow: var(--shadow);
                  border: 3px solid var(--color-border);
                  transition: transform 0.2s, border-color 0.2s;
                }
                .project-circle-item:hover .project-circle-img-wrap {
                  transform: scale(1.05);
                  border-color: var(--color-primary);
                }
                
                .project-circle-img-wrap img {
                  width: 100%%;
                  height: 100%%;
                  object-fit: cover;
                }
                
                .project-circle-subtitle {
                  font-size: 0.8rem;
                  color: var(--color-text-muted);
                }
                
                /* ── Latest Comic (Home) ─────────────────────────────────── */
                
                .home-latest-comic {
                  margin-bottom: 2rem;
                }
                
                /* ── Cards & Grid ───────────────────────────────────────── */
                
                .card-grid {
                  display: grid;
                  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
                  gap: 1.5rem;
                }
                
                .card {
                  background: var(--color-surface);
                  border-radius: var(--radius);
                  overflow: hidden;
                  box-shadow: var(--shadow);
                  transition: transform 0.2s, box-shadow 0.2s;
                }
                .card:hover {
                  transform: translateY(-2px);
                  box-shadow: 0 4px 16px rgba(0,0,0,0.12);
                }
                
                .card-image {
                  width: 100%%;
                  aspect-ratio: 3/4;
                  object-fit: cover;
                }
                
                .card-body {
                  padding: 1rem;
                }
                
                .card-body h3 {
                  font-size: 1.1rem;
                  margin-bottom: 0.25rem;
                }
                
                .card-body p {
                  color: var(--color-text-muted);
                  font-size: 0.9rem;
                }
                
                /* ── Section Headers ────────────────────────────────────── */
                
                .section-header {
                  display: flex;
                  align-items: baseline;
                  justify-content: space-between;
                  margin-bottom: 1.5rem;
                }
                
                .section-header h2 {
                  font-size: 1.75rem;
                }
                
                .section-header a {
                  font-size: 0.9rem;
                  font-weight: 500;
                }
                
                .section-spacer {
                  margin-top: 3rem;
                }
                
                /* ── Buttons ────────────────────────────────────────────── */
                
                .btn {
                  display: inline-flex;
                  align-items: center;
                  gap: 0.5rem;
                  padding: 0.6rem 1.25rem;
                  border-radius: var(--radius);
                  font-weight: 600;
                  font-size: 0.9rem;
                  cursor: pointer;
                  border: none;
                  transition: background 0.2s, color 0.2s;
                }
                
                .btn-primary {
                  background: var(--color-primary);
                  color: #fff;
                }
                .btn-primary:hover {
                  background: var(--color-accent);
                  color: #fff;
                }
                
                .btn-secondary {
                  background: var(--color-secondary);
                  color: var(--color-text);
                }
                
                .btn-lg {
                  padding: 0.85rem 2rem;
                  font-size: 1rem;
                }
                
                /* ── Masonry Portfolio Grid ──────────────────────────────── */
                
                .masonry-grid {
                  columns: 3;
                  column-gap: 1rem;
                }
                
                .masonry-item {
                  display: inline-block;
                  width: 100%%;
                  margin-bottom: 1rem;
                  break-inside: avoid;
                  border-radius: var(--radius);
                  overflow: hidden;
                  background: var(--color-surface);
                  box-shadow: var(--shadow);
                  transition: transform 0.2s;
                  cursor: pointer;
                }
                .masonry-item:hover {
                  transform: scale(1.02);
                }
                
                .masonry-item img {
                  width: 100%%;
                  display: block;
                }
                
                .masonry-caption {
                  padding: 0.6rem 0.8rem;
                  font-size: 0.85rem;
                  font-weight: 500;
                }
                
                /* ── Commissions Page ────────────────────────────────────── */
                
                .commissions-container {
                  max-width: 640px;
                  margin: 0 auto;
                }
                
                .commissions-container h1 {
                  font-size: 2rem;
                  margin-bottom: 0.75rem;
                }
                
                .commissions-intro {
                  color: var(--color-text-muted);
                  margin-bottom: 2rem;
                  line-height: 1.7;
                }
                
                .commissions-form {
                  display: flex;
                  flex-direction: column;
                  gap: 1.25rem;
                }
                
                .form-group {
                  display: flex;
                  flex-direction: column;
                  gap: 0.35rem;
                }
                
                .form-group label {
                  font-weight: 600;
                  font-size: 0.9rem;
                  color: var(--color-text);
                }
                
                .form-group .optional {
                  font-weight: 400;
                  color: var(--color-text-muted);
                  font-size: 0.8rem;
                }
                
                .form-group input,
                .form-group select,
                .form-group textarea {
                  padding: 0.65rem 0.85rem;
                  border: 1px solid var(--color-border);
                  border-radius: var(--radius);
                  font-family: var(--font-body);
                  font-size: 0.95rem;
                  color: var(--color-text);
                  background: var(--color-surface);
                  transition: border-color 0.2s, box-shadow 0.2s;
                }
                
                .form-group input:focus,
                .form-group select:focus,
                .form-group textarea:focus {
                  outline: none;
                  border-color: var(--color-primary);
                  box-shadow: 0 0 0 3px rgba(0,0,0,0.06);
                }
                
                .form-group textarea {
                  resize: vertical;
                  min-height: 100px;
                }
                
                /* ── Comic Reader ───────────────────────────────────────── */
                
                .reader-container {
                  max-width: 900px;
                  margin: 0 auto;
                }
                
                .reader-header {
                  text-align: center;
                  margin-bottom: 1.5rem;
                }
                
                .reader-header h1 {
                  font-size: 1.75rem;
                  margin-bottom: 0.25rem;
                }
                
                .reader-header .subtitle {
                  color: var(--color-text-muted);
                }
                
                .reader-viewport {
                  position: relative;
                  background: var(--color-surface);
                  border-radius: var(--radius);
                  overflow: hidden;
                  box-shadow: var(--shadow);
                  cursor: pointer;
                  min-height: 400px;
                  display: flex;
                  align-items: center;
                  justify-content: center;
                }
                
                .reader-viewport img {
                  width: 100%%;
                  display: block;
                }
                
                .reader-controls {
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  gap: 1rem;
                  margin-top: 1rem;
                  flex-wrap: wrap;
                }
                
                .reader-controls .btn {
                  min-width: 100px;
                  justify-content: center;
                }
                
                .page-indicator {
                  font-weight: 600;
                  color: var(--color-text-muted);
                  min-width: 80px;
                  text-align: center;
                }
                
                .reader-nav-issues {
                  display: flex;
                  justify-content: space-between;
                  margin-top: 2rem;
                  padding-top: 1rem;
                  border-top: 1px solid var(--color-border);
                }
                
                /* ── PhotoSwipe Caption ─────────────────────────────────── */
                
                .pswp__custom-caption {
                  color: #fff;
                  text-align: center;
                  padding: 0.75rem 1rem;
                  font-size: 0.95rem;
                  position: absolute;
                  bottom: 0;
                  left: 0;
                  right: 0;
                  background: linear-gradient(transparent, rgba(0,0,0,0.5));
                  pointer-events: none;
                }
                
                /* ── About Page ─────────────────────────────────────────── */
                
                .about-content {
                  max-width: 720px;
                  margin: 0 auto;
                }
                
                .about-content h1 {
                  font-size: 2rem;
                  margin-bottom: 1.5rem;
                }
                
                .about-text {
                  font-size: 1.05rem;
                  line-height: 1.8;
                  white-space: pre-wrap;
                }
                
                .social-links {
                  display: flex;
                  gap: 1rem;
                  flex-wrap: wrap;
                  margin-top: 2rem;
                }
                
                .social-link {
                  display: inline-flex;
                  align-items: center;
                  gap: 0.5rem;
                  padding: 0.5rem 1rem;
                  background: var(--color-surface);
                  border: 1px solid var(--color-border);
                  border-radius: var(--radius);
                  font-weight: 500;
                  transition: border-color 0.2s, background 0.2s;
                }
                .social-link:hover {
                  border-color: var(--color-primary);
                  background: var(--color-bg);
                }
                
                /* ── Responsive ─────────────────────────────────────────── */
                
                .hamburger {
                  display: none;
                  background: none;
                  border: none;
                  font-size: 1.5rem;
                  cursor: pointer;
                  color: #fff;
                }
                
                @media (max-width: 768px) {
                  .site-nav {
                    display: none;
                    position: absolute;
                    top: 100%%;
                    left: 0;
                    right: 0;
                    background: var(--color-primary);
                    flex-direction: column;
                    padding: 1rem;
                    box-shadow: var(--shadow);
                  }
                  .site-nav.open {
                    display: flex;
                  }
                  .hamburger {
                    display: block;
                  }
                  .hero-title {
                    font-size: 2.5rem;
                  }
                  .card-grid {
                    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
                  }
                  .masonry-grid {
                    columns: 2;
                  }
                  .home-about {
                    grid-template-columns: 1fr;
                  }
                  .project-circles {
                    gap: 1.5rem;
                  }
                  .project-circle-item {
                    width: 110px;
                  }
                  .project-circle-img-wrap {
                    width: 90px;
                    height: 90px;
                  }
                  .nav-buttons {
                    gap: 0.75rem;
                  }
                  .nav-btn {
                    padding: 0.6rem 1.25rem;
                    font-size: 0.9rem;
                  }
                }
                
                @media (max-width: 480px) {
                  .masonry-grid {
                    columns: 1;
                  }
                  .hero-title {
                    font-size: 2rem;
                  }
                }
                """.formatted(
                config.getPrimaryColor(),
                config.getSecondaryColor(),
                config.getAccentColor(),
                config.getHeadingFont(),
                config.getBodyFont(),
                taglineCount,
                cycleDuration,
                // Keyframe percentages: visible portion is ~30%% of each tagline's slot
                String.format("%.0f", (1.0 / taglineCount) * 80),
                String.format("%.0f", (1.0 / taglineCount) * 100)
        );
    }

    private String buildGoogleFontsImport(String headingFont, String bodyFont) {
        var heading = headingFont.replace(" ", "+");
        var body = bodyFont.replace(" ", "+");
        if (heading.equals(body)) {
            return "@import url('https://fonts.googleapis.com/css2?family=" + heading + ":wght@400;600;700&display=swap');\n";
        }
        return "@import url('https://fonts.googleapis.com/css2?family=" + heading + ":wght@400;600;700&family=" + body + ":wght@400;500;600&display=swap');\n";
    }

    private String loadStaticAsset(String classpath) {
        try {
            return new ClassPathResource(classpath).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load static asset: {}", classpath, e);
            return "/* Failed to load " + classpath + " */";
        }
    }
}

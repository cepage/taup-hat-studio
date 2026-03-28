package org.tanzu.thstudio.publish;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tanzu.thstudio.portfolio.PortfolioItemRepository;
import org.tanzu.thstudio.portfolio.PortfolioSetRepository;
import org.tanzu.thstudio.site.SiteConfigService;
import org.tanzu.thstudio.webcomic.WebcomicIssueRepository;
import org.tanzu.thstudio.webcomic.WebcomicPageRepository;
import org.tanzu.thstudio.webcomic.WebcomicSeriesRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;

/**
 * Orchestrates static site generation by fetching CMS content from repositories,
 * delegating rendering to {@link SiteRendererService}, and assembling the final
 * {@link GeneratedSite} for deployment via {@link FirebaseHostingService}.
 */
@Service
@Transactional(readOnly = true)
public class SiteGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(SiteGeneratorService.class);

    private final SiteRendererService renderer;
    private final SiteConfigService siteConfigService;
    private final WebcomicSeriesRepository seriesRepository;
    private final WebcomicIssueRepository issueRepository;
    private final WebcomicPageRepository pageRepository;
    private final PortfolioItemRepository portfolioRepository;
    private final PortfolioSetRepository portfolioSetRepository;

    public SiteGeneratorService(
            SiteRendererService renderer,
            SiteConfigService siteConfigService,
            WebcomicSeriesRepository seriesRepository,
            WebcomicIssueRepository issueRepository,
            WebcomicPageRepository pageRepository,
            PortfolioItemRepository portfolioRepository,
            PortfolioSetRepository portfolioSetRepository) {
        this.renderer = renderer;
        this.siteConfigService = siteConfigService;
        this.seriesRepository = seriesRepository;
        this.issueRepository = issueRepository;
        this.pageRepository = pageRepository;
        this.portfolioRepository = portfolioRepository;
        this.portfolioSetRepository = portfolioSetRepository;
    }

    /**
     * Generates the full static site and returns the result without uploading.
     */
    public GeneratedSite generate() {
        log.info("Starting static site generation...");
        var site = new GeneratedSite();
        var config = siteConfigService.getConfig();
        var activeSeries = seriesRepository.findByActiveTrueOrderBySortOrderAsc();
        var portfolioItems = portfolioRepository.findAllByOrderBySortOrderAsc();
        var portfolioSets = portfolioSetRepository.findAllByOrderBySortOrderAsc();

        // CSS
        site.addCss("css/style.css", renderer.renderStyleCss(config));

        // JS assets
        site.addJs("js/comic-reader.js", loadStaticAsset("site-assets/comic-reader.js"));
        site.addJs("js/portfolio-lightbox.js", loadStaticAsset("site-assets/portfolio-lightbox.js"));
        site.addJs("js/stars.js", loadStaticAsset("site-assets/stars.js"));
        site.addJs("js/set-viewer.js", loadStaticAsset("site-assets/set-viewer.js"));
        site.addJs("js/about-carousel.js", loadStaticAsset("site-assets/about-carousel.js"));

        // Image assets
        site.addBinary("images/star.png", loadStaticBinaryAsset("site-assets/star.png"), "image/png");

        // Home page — resolve latest issue for the first active series
        var latestIssue = activeSeries.isEmpty() ? null
                : issueRepository.findFirstBySeries_IdAndPublishedTrueOrderByIssueNumberDesc(
                        activeSeries.getFirst().getId()).orElse(null);
        var latestIssueSeries = latestIssue != null ? activeSeries.getFirst() : null;
        site.addHtml("index.html",
                renderer.renderHome(config, activeSeries, portfolioItems, latestIssue, latestIssueSeries));

        // Comics series list (with issue counts)
        var issueCountsBySeries = new LinkedHashMap<Long, Integer>();
        for (var series : activeSeries) {
            var publishedIssues = issueRepository.findBySeries_IdAndPublishedTrueOrderByIssueNumberDesc(series.getId());
            issueCountsBySeries.put(series.getId(), publishedIssues.size());
        }
        site.addHtml("comics/index.html", renderer.renderSeriesList(config, activeSeries, issueCountsBySeries));

        // Each series detail + issue readers
        for (var series : activeSeries) {
            var publishedIssues = issueRepository.findBySeries_IdAndPublishedTrueOrderByIssueNumberDesc(series.getId());
            site.addHtml("comics/" + series.getSlug() + "/index.html",
                    renderer.renderSeriesDetail(config, series, publishedIssues));

            for (var issue : publishedIssues) {
                var pages = pageRepository.findByIssue_IdOrderByPageNumberAsc(issue.getId());
                site.addHtml("comics/" + series.getSlug() + "/" + issue.getIssueNumber() + "/index.html",
                        renderer.renderIssueReader(config, series, issue, pages, publishedIssues));
            }
        }

        // Portfolio page
        var standaloneItems = portfolioItems.stream().filter(i -> i.getSetId() == null).toList();
        site.addHtml("portfolio/index.html", renderer.renderPortfolio(config, standaloneItems, portfolioSets));

        // Portfolio set viewer pages
        for (var set : portfolioSets) {
            var setItems = portfolioRepository.findBySetIdOrderBySetSortOrderAsc(set.getId());
            site.addHtml("portfolio/sets/" + set.getId() + "/index.html",
                    renderer.renderPortfolioSet(config, set, setItems));
        }

        // Commissions & About
        site.addHtml("commissions/index.html", renderer.renderCommissions(config));
        site.addHtml("about/index.html", renderer.renderAbout(config));

        log.info("Static site generation complete: {} files", site.fileCount());
        return site;
    }

    // ── Static asset loading ────────────────────────────────────────────────

    private String loadStaticAsset(String classpath) {
        try {
            return new ClassPathResource(classpath).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load static asset: {}", classpath, e);
            return "/* Failed to load " + classpath + " */";
        }
    }

    private byte[] loadStaticBinaryAsset(String classpath) {
        try {
            return new ClassPathResource(classpath).getContentAsByteArray();
        } catch (IOException e) {
            log.error("Failed to load static binary asset: {}", classpath, e);
            return new byte[0];
        }
    }
}

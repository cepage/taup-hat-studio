package org.tanzu.thstudio.publish;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.tanzu.thstudio.portfolio.PortfolioItem;
import org.tanzu.thstudio.portfolio.PortfolioItemRepository;
import org.tanzu.thstudio.portfolio.PortfolioSet;
import org.tanzu.thstudio.site.SiteConfig;
import org.tanzu.thstudio.webcomic.WebcomicIssue;
import org.tanzu.thstudio.webcomic.WebcomicPage;
import org.tanzu.thstudio.webcomic.WebcomicSeries;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles all Thymeleaf template rendering for the static site,
 * including HTML pages and the theme-driven CSS stylesheet.
 */
@Service
class SiteRendererService {

    private static final Logger log = LoggerFactory.getLogger(SiteRendererService.class);

    private final TemplateEngine templateEngine;
    private final PortfolioItemRepository portfolioRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    SiteRendererService(
            @Qualifier("siteTemplateEngine") TemplateEngine templateEngine,
            PortfolioItemRepository portfolioRepository) {
        this.templateEngine = templateEngine;
        this.portfolioRepository = portfolioRepository;
    }

    // ── CSS ──────────────────────────────────────────────────────────────────

    String renderStyleCss(SiteConfig config) {
        var customFontNames = parseCustomFonts(config.getCustomFonts());
        var googleFontsImport = buildGoogleFontsImport(config.getHeadingFont(), config.getBodyFont(), customFontNames);

        var taglines = parseTaglines(config.getSiteTaglines());
        int taglineCount = Math.max(taglines.size(), 1);
        double cycleDuration = taglineCount * 3.0;

        var ctx = new Context();
        ctx.setVariable("googleFontsImport", googleFontsImport);
        ctx.setVariable("primaryColor", config.getPrimaryColor());
        ctx.setVariable("secondaryColor", config.getSecondaryColor());
        ctx.setVariable("accentColor", config.getAccentColor());
        ctx.setVariable("headingFont", config.getHeadingFont());
        ctx.setVariable("bodyFont", config.getBodyFont());
        ctx.setVariable("taglineCount", taglineCount);
        ctx.setVariable("cycleDuration", String.format("%.1f", cycleDuration));
        ctx.setVariable("taglineHoldPct", String.format("%.0f", (1.0 / taglineCount) * 70));
        ctx.setVariable("taglineFadePct", String.format("%.0f", (1.0 / taglineCount) * 90));

        return templateEngine.process("style", ctx);
    }

    // ── Page rendering ──────────────────────────────────────────────────────

    String renderHome(SiteConfig config, List<WebcomicSeries> activeSeries,
                      List<PortfolioItem> portfolioItems, WebcomicIssue latestIssue,
                      WebcomicSeries latestIssueSeries) {
        var ctx = baseContext(config);
        ctx.setVariable("activeSeries", activeSeries);
        ctx.setVariable("portfolioItems", portfolioItems.stream().limit(8).toList());
        ctx.setVariable("taglines", parseTaglines(config.getSiteTaglines()));

        if (latestIssue != null) {
            ctx.setVariable("latestIssue", latestIssue);
            ctx.setVariable("latestIssueSeries", latestIssueSeries);
        }

        return templateEngine.process("home", ctx);
    }

    String renderSeriesList(SiteConfig config, List<WebcomicSeries> activeSeries,
                            Map<Long, Integer> issueCountsBySeries) {
        var ctx = baseContext(config);
        ctx.setVariable("activeSeries", activeSeries);
        for (var series : activeSeries) {
            ctx.setVariable("issueCount_" + series.getId(), issueCountsBySeries.getOrDefault(series.getId(), 0));
        }
        return templateEngine.process("series-list", ctx);
    }

    String renderSeriesDetail(SiteConfig config, WebcomicSeries series,
                              List<WebcomicIssue> publishedIssues) {
        var ctx = baseContext(config);
        ctx.setVariable("series", series);
        ctx.setVariable("issues", publishedIssues);
        return templateEngine.process("series-detail", ctx);
    }

    String renderIssueReader(SiteConfig config, WebcomicSeries series,
                             WebcomicIssue issue, List<WebcomicPage> pages,
                             List<WebcomicIssue> allIssues) {
        var ctx = baseContext(config);
        ctx.setVariable("series", series);
        ctx.setVariable("issue", issue);
        ctx.setVariable("pages", pages);

        var pagesJson = pages.stream().map(p -> {
            var map = new LinkedHashMap<String, Object>();
            map.put("pageNumber", p.getPageNumber());
            map.put("imageUrl", p.getImageUrl());
            map.put("optimizedUrl", p.getOptimizedUrl());
            return map;
        }).toList();
        ctx.setVariable("pagesJson", pagesJson);

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

    String renderPortfolio(SiteConfig config, List<PortfolioItem> standaloneItems,
                           List<PortfolioSet> sets) {
        var ctx = baseContext(config);

        record PortfolioEntry(String type, int sortOrder, PortfolioItem item, PortfolioSet set, int itemCount) {}

        var entries = new ArrayList<PortfolioEntry>();
        for (var item : standaloneItems) {
            entries.add(new PortfolioEntry("item", item.getSortOrder(), item, null, 0));
        }
        for (var set : sets) {
            int count = portfolioRepository.findBySetIdOrderBySetSortOrderAsc(set.getId()).size();
            entries.add(new PortfolioEntry("set", set.getSortOrder(), null, set, count));
        }
        entries.sort(Comparator.comparingInt(PortfolioEntry::sortOrder));

        ctx.setVariable("entries", entries);
        return templateEngine.process("portfolio", ctx);
    }

    String renderPortfolioSet(SiteConfig config, PortfolioSet set, List<PortfolioItem> items) {
        var ctx = baseContext(config);
        ctx.setVariable("set", set);
        ctx.setVariable("items", items);

        var itemsJson = items.stream().map(item -> {
            var map = new LinkedHashMap<String, Object>();
            map.put("id", item.getId());
            map.put("title", item.getTitle());
            map.put("imageUrl", item.getImageUrl());
            map.put("optimizedUrl", item.getOptimizedUrl());
            map.put("thumbnailUrl", item.getThumbnailUrl());
            return map;
        }).toList();
        ctx.setVariable("itemsJson", itemsJson);

        return templateEngine.process("portfolio-set", ctx);
    }

    String renderAbout(SiteConfig config) {
        var ctx = baseContext(config);
        ctx.setVariable("socialLinks", parseSocialLinks(config.getSocialLinks()));
        return templateEngine.process("about", ctx);
    }

    String renderCommissions(SiteConfig config) {
        var ctx = baseContext(config);
        ctx.setVariable("commissionsEmail", config.getCommissionsEmail());
        return templateEngine.process("commissions", ctx);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private Context baseContext(SiteConfig config) {
        var ctx = new Context();
        ctx.setVariable("config", config);
        ctx.setVariable("siteName", config.getSiteName());
        ctx.setVariable("adobeFontsUrl", config.getAdobeFontsUrl());
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

    List<String> parseTaglines(String taglines) {
        if (taglines == null || taglines.isBlank()) {
            return List.of();
        }
        return Arrays.stream(taglines.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private String buildGoogleFontsImport(String headingFont, String bodyFont, List<String> customFontNames) {
        boolean headingIsCustom = customFontNames.contains(headingFont);
        boolean bodyIsCustom = customFontNames.contains(bodyFont);

        if (headingIsCustom && bodyIsCustom) {
            return "";
        }

        var families = new ArrayList<String>();
        if (!headingIsCustom) {
            families.add(headingFont.replace(" ", "+") + ":wght@400;600;700");
        }
        if (!bodyIsCustom && !bodyFont.equals(headingFont)) {
            families.add(bodyFont.replace(" ", "+") + ":wght@400;500;600");
        }

        if (families.isEmpty()) {
            return "";
        }

        return "@import url('https://fonts.googleapis.com/css2?family="
                + String.join("&family=", families) + "&display=swap');\n";
    }

    private List<String> parseCustomFonts(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse custom fonts JSON: {}", e.getMessage());
            return List.of();
        }
    }
}

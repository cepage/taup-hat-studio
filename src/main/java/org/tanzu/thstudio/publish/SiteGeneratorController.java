package org.tanzu.thstudio.publish;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * REST API for static site generation.
 * <p>
 * POST /api/publish/generate — generates the static site and uploads to GCS staging
 * GET  /api/publish/preview/{path} — (future) preview a generated page
 */
@RestController
@RequestMapping("/api/publish")
public class SiteGeneratorController {

    private static final Logger log = LoggerFactory.getLogger(SiteGeneratorController.class);

    private final SiteGeneratorService generatorService;

    public SiteGeneratorController(SiteGeneratorService generatorService) {
        this.generatorService = generatorService;
    }

    /**
     * Generates the complete static site and uploads all files to GCS staging.
     * Returns a summary of the generation result.
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate() {
        log.info("Static site generation requested");
        try {
            int fileCount = generatorService.generateAndUpload();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "fileCount", fileCount,
                    "timestamp", LocalDateTime.now().toString()
            ));
        } catch (Exception e) {
            log.error("Static site generation failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage() != null ? e.getMessage() : "Unknown error",
                    "timestamp", LocalDateTime.now().toString()
            ));
        }
    }

    /**
     * Generates the static site in-memory (without uploading) and returns a summary.
     * Useful for validating the site before publishing.
     */
    @GetMapping("/preview-summary")
    public ResponseEntity<Map<String, Object>> previewSummary() {
        try {
            var site = generatorService.generate();
            var filePaths = site.getFiles().keySet().stream().sorted().toList();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "fileCount", site.fileCount(),
                    "files", filePaths,
                    "timestamp", LocalDateTime.now().toString()
            ));
        } catch (Exception e) {
            log.error("Static site preview failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage() != null ? e.getMessage() : "Unknown error"
            ));
        }
    }
}

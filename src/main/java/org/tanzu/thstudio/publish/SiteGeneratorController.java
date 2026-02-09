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
 * REST API for static site generation, preview, and deployment.
 * <p>
 * POST /api/publish/preview  — generates the site and deploys to a Firebase preview channel
 * POST /api/publish/deploy   — generates the site and deploys to the Firebase live channel
 * GET  /api/publish/preview-summary — generates in-memory and returns a file listing (no deploy)
 */
@RestController
@RequestMapping("/api/publish")
public class SiteGeneratorController {

    private static final Logger log = LoggerFactory.getLogger(SiteGeneratorController.class);

    private final SiteGeneratorService generatorService;
    private final FirebaseHostingService firebaseHostingService;

    public SiteGeneratorController(SiteGeneratorService generatorService,
                                   FirebaseHostingService firebaseHostingService) {
        this.generatorService = generatorService;
        this.firebaseHostingService = firebaseHostingService;
    }

    /**
     * Generates the static site and deploys it to a Firebase preview channel.
     * Returns the preview URL for the user to view the staged site.
     */
    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> preview() {
        log.info("Preview deployment requested");
        try {
            var site = generatorService.generate();
            String previewUrl = firebaseHostingService.deployToPreview(site);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "previewUrl", previewUrl,
                    "fileCount", site.fileCount(),
                    "timestamp", LocalDateTime.now().toString()
            ));
        } catch (Exception e) {
            log.error("Preview deployment failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage() != null ? e.getMessage() : "Unknown error",
                    "timestamp", LocalDateTime.now().toString()
            ));
        }
    }

    /**
     * Generates the static site and deploys it to the Firebase live channel (production).
     */
    @PostMapping("/deploy")
    public ResponseEntity<Map<String, Object>> deploy() {
        log.info("Production deployment requested");
        try {
            var site = generatorService.generate();
            firebaseHostingService.deployToLive(site);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "fileCount", site.fileCount(),
                    "timestamp", LocalDateTime.now().toString()
            ));
        } catch (Exception e) {
            log.error("Production deployment failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage() != null ? e.getMessage() : "Unknown error",
                    "timestamp", LocalDateTime.now().toString()
            ));
        }
    }

    /**
     * Generates the static site in-memory (without deploying) and returns a summary.
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

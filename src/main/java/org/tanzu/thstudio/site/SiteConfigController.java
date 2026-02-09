package org.tanzu.thstudio.site;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.tanzu.thstudio.image.ImageProcessingService;
import org.tanzu.thstudio.image.StorageService;

import java.io.IOException;

@RestController
@RequestMapping("/api/site-config")
public class SiteConfigController {

    private final SiteConfigService service;
    private final ImageProcessingService imageProcessingService;
    private final StorageService storageService;

    public SiteConfigController(SiteConfigService service,
                                ImageProcessingService imageProcessingService,
                                StorageService storageService) {
        this.service = service;
        this.imageProcessingService = imageProcessingService;
        this.storageService = storageService;
    }

    @GetMapping
    public ResponseEntity<SiteConfig> getConfig() {
        return ResponseEntity.ok(service.getConfig());
    }

    @PutMapping
    public ResponseEntity<SiteConfig> updateConfig(@RequestBody SiteConfig config) {
        return ResponseEntity.ok(service.updateConfig(config));
    }

    /**
     * Uploads a hero image, replacing the previous one if it exists.
     */
    @PutMapping(value = "/hero-image", consumes = "multipart/form-data")
    public ResponseEntity<SiteConfig> uploadHeroImage(@RequestParam("file") MultipartFile file)
            throws IOException {
        var config = service.getConfig();

        // Delete old hero image from GCS if present
        deleteHeroImageAssets(config.getHeroImageUrl());

        // Process and upload new hero image
        String basePath = "images/site";
        String filename = "hero-" + System.currentTimeMillis();
        var urls = imageProcessingService.processAndUpload(file, basePath, filename);

        config.setHeroImageUrl(urls.optimizedUrl());
        return ResponseEntity.ok(service.save(config));
    }

    /**
     * Removes the current hero image.
     */
    @DeleteMapping("/hero-image")
    public ResponseEntity<SiteConfig> deleteHeroImage() {
        var config = service.getConfig();
        deleteHeroImageAssets(config.getHeroImageUrl());
        config.setHeroImageUrl(null);
        return ResponseEntity.ok(service.save(config));
    }

    private void deleteHeroImageAssets(String url) {
        if (url != null && url.contains("storage.googleapis.com/")) {
            // Delete all variants under the site images path
            storageService.deleteByPrefix("images/site/");
        }
    }
}

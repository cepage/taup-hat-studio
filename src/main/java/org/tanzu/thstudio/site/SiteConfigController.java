package org.tanzu.thstudio.site;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/site-config")
public class SiteConfigController {

    private final SiteConfigService service;

    public SiteConfigController(SiteConfigService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<SiteConfig> getConfig() {
        return ResponseEntity.ok(service.getConfig());
    }

    @PutMapping
    public ResponseEntity<SiteConfig> updateConfig(@RequestBody SiteConfig config) {
        return ResponseEntity.ok(service.updateConfig(config));
    }
}

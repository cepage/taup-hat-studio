package org.tanzu.thstudio.site;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SiteConfigService {

    private final SiteConfigRepository repository;

    public SiteConfigService(SiteConfigRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns the single site configuration row, creating a default one if none exists.
     */
    @Transactional(readOnly = true)
    public SiteConfig getConfig() {
        return repository.findAll().stream()
                .findFirst()
                .orElseGet(() -> repository.save(new SiteConfig()));
    }

    public SiteConfig updateConfig(SiteConfig updated) {
        var existing = getConfig();
        existing.setSiteName(updated.getSiteName());
        existing.setPrimaryColor(updated.getPrimaryColor());
        existing.setSecondaryColor(updated.getSecondaryColor());
        existing.setAccentColor(updated.getAccentColor());
        existing.setHeadingFont(updated.getHeadingFont());
        existing.setBodyFont(updated.getBodyFont());
        existing.setAboutText(updated.getAboutText());
        existing.setBigcartelUrl(updated.getBigcartelUrl());
        existing.setSocialLinks(updated.getSocialLinks());
        // Note: heroImageUrl is managed separately via the hero-image endpoint
        return repository.save(existing);
    }

    /**
     * Saves the site config entity directly (used by hero image upload).
     */
    public SiteConfig save(SiteConfig config) {
        return repository.save(config);
    }
}

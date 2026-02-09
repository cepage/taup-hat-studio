package org.tanzu.thstudio.site;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "site_config")
public class SiteConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String siteName = "TaupHat";

    @Column(nullable = false)
    private String primaryColor = "#5C4033";

    @Column(nullable = false)
    private String secondaryColor = "#D4A574";

    @Column(nullable = false)
    private String accentColor = "#8B6914";

    @Column(nullable = false)
    private String headingFont = "Playfair Display";

    @Column(nullable = false)
    private String bodyFont = "Open Sans";

    private String heroImageUrl;

    @Column(columnDefinition = "TEXT")
    private String aboutText;

    private String bigcartelUrl;

    @Column(columnDefinition = "TEXT")
    private String socialLinks;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }

    public String getPrimaryColor() { return primaryColor; }
    public void setPrimaryColor(String primaryColor) { this.primaryColor = primaryColor; }

    public String getSecondaryColor() { return secondaryColor; }
    public void setSecondaryColor(String secondaryColor) { this.secondaryColor = secondaryColor; }

    public String getAccentColor() { return accentColor; }
    public void setAccentColor(String accentColor) { this.accentColor = accentColor; }

    public String getHeadingFont() { return headingFont; }
    public void setHeadingFont(String headingFont) { this.headingFont = headingFont; }

    public String getBodyFont() { return bodyFont; }
    public void setBodyFont(String bodyFont) { this.bodyFont = bodyFont; }

    public String getHeroImageUrl() { return heroImageUrl; }
    public void setHeroImageUrl(String heroImageUrl) { this.heroImageUrl = heroImageUrl; }

    public String getAboutText() { return aboutText; }
    public void setAboutText(String aboutText) { this.aboutText = aboutText; }

    public String getBigcartelUrl() { return bigcartelUrl; }
    public void setBigcartelUrl(String bigcartelUrl) { this.bigcartelUrl = bigcartelUrl; }

    public String getSocialLinks() { return socialLinks; }
    public void setSocialLinks(String socialLinks) { this.socialLinks = socialLinks; }
}

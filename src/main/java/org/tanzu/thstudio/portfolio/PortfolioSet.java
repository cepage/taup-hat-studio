package org.tanzu.thstudio.portfolio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "portfolio_set")
public class PortfolioSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String iconImageUrl;

    private String iconThumbnailUrl;

    private String iconOptimizedUrl;

    @Column(nullable = false)
    private Integer sortOrder = 0;

    @OneToMany(mappedBy = "set")
    @OrderBy("setSortOrder ASC")
    private List<PortfolioItem> items = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getIconImageUrl() { return iconImageUrl; }
    public void setIconImageUrl(String iconImageUrl) { this.iconImageUrl = iconImageUrl; }

    public String getIconThumbnailUrl() { return iconThumbnailUrl; }
    public void setIconThumbnailUrl(String iconThumbnailUrl) { this.iconThumbnailUrl = iconThumbnailUrl; }

    public String getIconOptimizedUrl() { return iconOptimizedUrl; }
    public void setIconOptimizedUrl(String iconOptimizedUrl) { this.iconOptimizedUrl = iconOptimizedUrl; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public List<PortfolioItem> getItems() { return items; }
    public void setItems(List<PortfolioItem> items) { this.items = items; }
}

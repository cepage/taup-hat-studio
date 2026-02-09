package org.tanzu.thstudio.webcomic;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "webcomic_page")
public class WebcomicPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id", nullable = false)
    @JsonIgnore
    private WebcomicIssue issue;

    @Column(nullable = false)
    private Integer pageNumber;

    @Column(nullable = false)
    private String imageUrl;

    private String thumbnailUrl;

    private String optimizedUrl;

    // Transient field for JSON serialization
    public Long getIssueId() {
        return issue != null ? issue.getId() : null;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public WebcomicIssue getIssue() { return issue; }
    public void setIssue(WebcomicIssue issue) { this.issue = issue; }

    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getThumbnailUrl() { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl) { this.thumbnailUrl = thumbnailUrl; }

    public String getOptimizedUrl() { return optimizedUrl; }
    public void setOptimizedUrl(String optimizedUrl) { this.optimizedUrl = optimizedUrl; }
}

package org.tanzu.thstudio.webcomic;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tanzu.thstudio.image.StorageService;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/webcomic/series/{seriesId}/issues")
public class WebcomicIssueController {

    private final WebcomicIssueRepository issueRepository;
    private final WebcomicSeriesRepository seriesRepository;
    private final StorageService storageService;

    public WebcomicIssueController(WebcomicIssueRepository issueRepository,
                                   WebcomicSeriesRepository seriesRepository,
                                   StorageService storageService) {
        this.issueRepository = issueRepository;
        this.seriesRepository = seriesRepository;
        this.storageService = storageService;
    }

    @GetMapping
    public ResponseEntity<List<WebcomicIssue>> listBySeries(@PathVariable Long seriesId) {
        if (!seriesRepository.existsById(seriesId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(issueRepository.findBySeries_IdOrderByIssueNumberDesc(seriesId));
    }

    @GetMapping("/{issueId}")
    public ResponseEntity<WebcomicIssue> getById(@PathVariable Long seriesId,
                                                 @PathVariable Long issueId) {
        return issueRepository.findById(issueId)
                .filter(issue -> issue.getSeries().getId().equals(seriesId))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<WebcomicIssue> create(@PathVariable Long seriesId,
                                                @RequestBody WebcomicIssue issue) {
        return seriesRepository.findById(seriesId)
                .map(series -> {
                    issue.setId(null);
                    issue.setSeries(series);
                    // Auto-assign next issue number if not specified
                    if (issue.getIssueNumber() == null) {
                        var latestIssue = issueRepository
                                .findFirstBySeries_IdAndPublishedTrueOrderByIssueNumberDesc(seriesId);
                        int nextNumber = latestIssue.map(i -> i.getIssueNumber() + 1).orElse(1);
                        // Also check unpublished issues
                        var allIssues = issueRepository.findBySeries_IdOrderByIssueNumberDesc(seriesId);
                        if (!allIssues.isEmpty()) {
                            nextNumber = Math.max(nextNumber, allIssues.getFirst().getIssueNumber() + 1);
                        }
                        issue.setIssueNumber(nextNumber);
                    }
                    var saved = issueRepository.save(issue);
                    var uri = URI.create("/api/webcomic/series/" + seriesId + "/issues/" + saved.getId());
                    return ResponseEntity.created(uri).body(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{issueId}")
    public ResponseEntity<WebcomicIssue> update(@PathVariable Long seriesId,
                                                @PathVariable Long issueId,
                                                @RequestBody WebcomicIssue issue) {
        return issueRepository.findById(issueId)
                .filter(existing -> existing.getSeries().getId().equals(seriesId))
                .map(existing -> {
                    existing.setTitle(issue.getTitle());
                    existing.setIssueNumber(issue.getIssueNumber());
                    existing.setCoverImageUrl(issue.getCoverImageUrl());
                    existing.setPublishDate(issue.getPublishDate());
                    existing.setPublished(issue.getPublished());
                    return ResponseEntity.ok(issueRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{issueId}")
    public ResponseEntity<Void> delete(@PathVariable Long seriesId,
                                       @PathVariable Long issueId) {
        return issueRepository.findById(issueId)
                .filter(existing -> existing.getSeries().getId().equals(seriesId))
                .map(existing -> {
                    // Delete all GCS assets for this issue
                    storageService.deleteByPrefix("images/webcomic/" + seriesId + "/" + issueId + "/");
                    issueRepository.delete(existing);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }
}

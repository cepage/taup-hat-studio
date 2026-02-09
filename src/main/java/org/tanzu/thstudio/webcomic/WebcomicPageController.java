package org.tanzu.thstudio.webcomic;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.tanzu.thstudio.image.ImageProcessingService;
import org.tanzu.thstudio.image.StorageService;

import java.io.IOException;
import java.net.URI;
import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/webcomic/series/{seriesId}/issues/{issueId}/pages")
public class WebcomicPageController {

    private final WebcomicPageRepository pageRepository;
    private final WebcomicIssueRepository issueRepository;
    private final ImageProcessingService imageProcessingService;
    private final StorageService storageService;

    public WebcomicPageController(WebcomicPageRepository pageRepository,
                                  WebcomicIssueRepository issueRepository,
                                  ImageProcessingService imageProcessingService,
                                  StorageService storageService) {
        this.pageRepository = pageRepository;
        this.issueRepository = issueRepository;
        this.imageProcessingService = imageProcessingService;
        this.storageService = storageService;
    }

    @GetMapping
    public ResponseEntity<List<WebcomicPage>> listByIssue(@PathVariable Long seriesId,
                                                          @PathVariable Long issueId) {
        return issueRepository.findById(issueId)
                .filter(issue -> issue.getSeries().getId().equals(seriesId))
                .map(issue -> ResponseEntity.ok(pageRepository.findByIssue_IdOrderByPageNumberAsc(issueId)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<WebcomicPage> upload(@PathVariable Long seriesId,
                                               @PathVariable Long issueId,
                                               @RequestParam("file") MultipartFile file) throws IOException {
        return issueRepository.findById(issueId)
                .filter(issue -> issue.getSeries().getId().equals(seriesId))
                .map(issue -> {
                    try {
                        // Determine next page number
                        var existingPages = pageRepository.findByIssue_IdOrderByPageNumberAsc(issueId);
                        int nextPageNumber = existingPages.isEmpty() ? 1
                                : existingPages.getLast().getPageNumber() + 1;

                        // Process and upload image to GCS
                        String basePath = "images/webcomic/" + seriesId + "/" + issueId;
                        String filename = "page-" + String.format("%03d", nextPageNumber);
                        var urls = imageProcessingService.processAndUpload(file, basePath, filename);

                        // Create page entity
                        var page = new WebcomicPage();
                        page.setIssue(issue);
                        page.setPageNumber(nextPageNumber);
                        page.setImageUrl(urls.originalUrl());
                        page.setThumbnailUrl(urls.thumbnailUrl());
                        page.setOptimizedUrl(urls.optimizedUrl());

                        var saved = pageRepository.save(page);
                        var uri = URI.create("/api/webcomic/series/" + seriesId
                                + "/issues/" + issueId + "/pages/" + saved.getId());
                        return ResponseEntity.created(uri).body(saved);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to process image upload", e);
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/reorder")
    @Transactional
    public ResponseEntity<List<WebcomicPage>> reorder(@PathVariable Long seriesId,
                                                      @PathVariable Long issueId,
                                                      @RequestBody List<Long> orderedPageIds) {
        return issueRepository.findById(issueId)
                .filter(issue -> issue.getSeries().getId().equals(seriesId))
                .map(issue -> {
                    var pages = pageRepository.findAllById(orderedPageIds);
                    // First pass: set temporary negative page numbers to avoid
                    // unique constraint violations on (issue_id, page_number)
                    for (int i = 0; i < pages.size(); i++) {
                        pages.get(i).setPageNumber(-(i + 1));
                    }
                    pageRepository.saveAllAndFlush(pages);
                    // Second pass: assign the final page numbers
                    for (int i = 0; i < orderedPageIds.size(); i++) {
                        Long targetId = orderedPageIds.get(i);
                        for (var page : pages) {
                            if (page.getId().equals(targetId)) {
                                page.setPageNumber(i + 1);
                                break;
                            }
                        }
                    }
                    var saved = pageRepository.saveAllAndFlush(pages);
                    saved.sort(Comparator.comparing(WebcomicPage::getPageNumber));
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{pageId}")
    public ResponseEntity<Void> delete(@PathVariable Long seriesId,
                                       @PathVariable Long issueId,
                                       @PathVariable Long pageId) {
        return pageRepository.findById(pageId)
                .filter(page -> page.getIssue().getId().equals(issueId))
                .map(page -> {
                    // Delete GCS assets for this page
                    deletePageAssets(page);
                    pageRepository.delete(page);
                    // Re-sequence remaining pages
                    var remaining = pageRepository.findByIssue_IdOrderByPageNumberAsc(issueId);
                    for (int i = 0; i < remaining.size(); i++) {
                        remaining.get(i).setPageNumber(i + 1);
                    }
                    pageRepository.saveAll(remaining);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private void deletePageAssets(WebcomicPage page) {
        deleteGcsUrl(page.getImageUrl());
        deleteGcsUrl(page.getOptimizedUrl());
        deleteGcsUrl(page.getThumbnailUrl());
    }

    private void deleteGcsUrl(String url) {
        if (url != null && url.contains("storage.googleapis.com/")) {
            // URL format: https://storage.googleapis.com/{bucket}/{path}
            String path = url.substring(url.indexOf("storage.googleapis.com/") + "storage.googleapis.com/".length());
            // Remove the bucket name prefix
            int slashIndex = path.indexOf('/');
            if (slashIndex > 0) {
                storageService.delete(path.substring(slashIndex + 1));
            }
        }
    }
}

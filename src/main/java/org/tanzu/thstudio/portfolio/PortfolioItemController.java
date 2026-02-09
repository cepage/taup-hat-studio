package org.tanzu.thstudio.portfolio;

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
import java.util.List;

@RestController
@RequestMapping("/api/portfolio")
public class PortfolioItemController {

    private final PortfolioItemRepository portfolioRepository;
    private final ImageProcessingService imageProcessingService;
    private final StorageService storageService;

    public PortfolioItemController(PortfolioItemRepository portfolioRepository,
                                   ImageProcessingService imageProcessingService,
                                   StorageService storageService) {
        this.portfolioRepository = portfolioRepository;
        this.imageProcessingService = imageProcessingService;
        this.storageService = storageService;
    }

    @GetMapping
    public List<PortfolioItem> listAll() {
        return portfolioRepository.findAllByOrderBySortOrderAsc();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PortfolioItem> getById(@PathVariable Long id) {
        return portfolioRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<PortfolioItem> create(@RequestParam("file") MultipartFile file,
                                                @RequestParam("title") String title,
                                                @RequestParam(value = "description", required = false) String description,
                                                @RequestParam(value = "category", required = false) String category)
            throws IOException {

        // Determine next sort order
        var existing = portfolioRepository.findAllByOrderBySortOrderAsc();
        int nextSortOrder = existing.isEmpty() ? 0 : existing.getLast().getSortOrder() + 1;

        // Process and upload image to GCS
        String basePath = "images/portfolio";
        String filename = "item-" + System.currentTimeMillis();
        var urls = imageProcessingService.processAndUpload(file, basePath, filename);

        // Create portfolio item
        var item = new PortfolioItem();
        item.setTitle(title);
        item.setDescription(description);
        item.setCategory(category);
        item.setSortOrder(nextSortOrder);
        item.setImageUrl(urls.originalUrl());
        item.setThumbnailUrl(urls.thumbnailUrl());
        item.setOptimizedUrl(urls.optimizedUrl());

        var saved = portfolioRepository.save(item);
        return ResponseEntity
                .created(URI.create("/api/portfolio/" + saved.getId()))
                .body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PortfolioItem> update(@PathVariable Long id, @RequestBody PortfolioItem updated) {
        return portfolioRepository.findById(id)
                .map(existing -> {
                    existing.setTitle(updated.getTitle());
                    existing.setDescription(updated.getDescription());
                    existing.setCategory(updated.getCategory());
                    return ResponseEntity.ok(portfolioRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/image")
    public ResponseEntity<PortfolioItem> updateImage(@PathVariable Long id,
                                                     @RequestParam("file") MultipartFile file)
            throws IOException {
        return portfolioRepository.findById(id)
                .map(existing -> {
                    try {
                        // Delete old GCS assets
                        deleteItemAssets(existing);

                        // Process and upload new image
                        String basePath = "images/portfolio";
                        String filename = "item-" + System.currentTimeMillis();
                        var urls = imageProcessingService.processAndUpload(file, basePath, filename);

                        existing.setImageUrl(urls.originalUrl());
                        existing.setThumbnailUrl(urls.thumbnailUrl());
                        existing.setOptimizedUrl(urls.optimizedUrl());

                        return ResponseEntity.ok(portfolioRepository.save(existing));
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to process image upload", e);
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return portfolioRepository.findById(id)
                .map(item -> {
                    deleteItemAssets(item);
                    portfolioRepository.delete(item);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/reorder")
    @Transactional
    public List<PortfolioItem> reorder(@RequestBody List<Long> orderedIds) {
        var allItems = portfolioRepository.findAllById(orderedIds);
        for (int i = 0; i < orderedIds.size(); i++) {
            Long targetId = orderedIds.get(i);
            for (var item : allItems) {
                if (item.getId().equals(targetId)) {
                    item.setSortOrder(i);
                    break;
                }
            }
        }
        return portfolioRepository.saveAll(allItems);
    }

    private void deleteItemAssets(PortfolioItem item) {
        deleteGcsUrl(item.getImageUrl());
        deleteGcsUrl(item.getOptimizedUrl());
        deleteGcsUrl(item.getThumbnailUrl());
    }

    private void deleteGcsUrl(String url) {
        if (url != null && url.contains("storage.googleapis.com/")) {
            String path = url.substring(url.indexOf("storage.googleapis.com/") + "storage.googleapis.com/".length());
            int slashIndex = path.indexOf('/');
            if (slashIndex > 0) {
                storageService.delete(path.substring(slashIndex + 1));
            }
        }
    }
}

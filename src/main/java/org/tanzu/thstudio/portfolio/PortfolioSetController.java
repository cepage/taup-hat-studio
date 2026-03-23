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
@RequestMapping("/api/portfolio-sets")
public class PortfolioSetController {

    private final PortfolioSetRepository setRepository;
    private final PortfolioItemRepository itemRepository;
    private final ImageProcessingService imageProcessingService;
    private final StorageService storageService;

    public PortfolioSetController(PortfolioSetRepository setRepository,
                                  PortfolioItemRepository itemRepository,
                                  ImageProcessingService imageProcessingService,
                                  StorageService storageService) {
        this.setRepository = setRepository;
        this.itemRepository = itemRepository;
        this.imageProcessingService = imageProcessingService;
        this.storageService = storageService;
    }

    @GetMapping
    public List<PortfolioSet> listAll() {
        return setRepository.findAllByOrderBySortOrderAsc();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PortfolioSet> getById(@PathVariable Long id) {
        return setRepository.findById(id)
                .map(set -> {
                    // Force-initialize the items collection for serialization
                    set.getItems().size();
                    return ResponseEntity.ok(set);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<PortfolioSet> create(@RequestParam("file") MultipartFile file,
                                               @RequestParam("title") String title,
                                               @RequestParam(value = "description", required = false) String description)
            throws IOException {

        var existing = setRepository.findAllByOrderBySortOrderAsc();
        int nextSortOrder = existing.isEmpty() ? 0 : existing.getLast().getSortOrder() + 1;

        String basePath = "images/portfolio-sets";
        String filename = "set-" + System.currentTimeMillis();
        var urls = imageProcessingService.processAndUpload(file, basePath, filename);

        var set = new PortfolioSet();
        set.setTitle(title);
        set.setDescription(description);
        set.setSortOrder(nextSortOrder);
        set.setIconImageUrl(urls.originalUrl());
        set.setIconThumbnailUrl(urls.thumbnailUrl());
        set.setIconOptimizedUrl(urls.optimizedUrl());

        var saved = setRepository.save(set);
        return ResponseEntity
                .created(URI.create("/api/portfolio-sets/" + saved.getId()))
                .body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PortfolioSet> update(@PathVariable Long id, @RequestBody PortfolioSet updated) {
        return setRepository.findById(id)
                .map(existing -> {
                    existing.setTitle(updated.getTitle());
                    existing.setDescription(updated.getDescription());
                    return ResponseEntity.ok(setRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/icon")
    public ResponseEntity<PortfolioSet> updateIcon(@PathVariable Long id,
                                                   @RequestParam("file") MultipartFile file)
            throws IOException {
        return setRepository.findById(id)
                .map(existing -> {
                    try {
                        deleteIconAssets(existing);

                        String basePath = "images/portfolio-sets";
                        String filename = "set-" + System.currentTimeMillis();
                        var urls = imageProcessingService.processAndUpload(file, basePath, filename);

                        existing.setIconImageUrl(urls.originalUrl());
                        existing.setIconThumbnailUrl(urls.thumbnailUrl());
                        existing.setIconOptimizedUrl(urls.optimizedUrl());

                        return ResponseEntity.ok(setRepository.save(existing));
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to process icon upload", e);
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return setRepository.findById(id)
                .map(set -> {
                    // Ungroup all items (don't delete them)
                    var items = itemRepository.findBySetIdOrderBySetSortOrderAsc(id);
                    for (var item : items) {
                        item.setSet(null);
                        item.setSetSortOrder(0);
                    }
                    itemRepository.saveAll(items);

                    deleteIconAssets(set);
                    setRepository.delete(set);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/items")
    @Transactional
    public ResponseEntity<List<PortfolioItem>> setItems(@PathVariable Long id,
                                                        @RequestBody List<Long> orderedItemIds) {
        return setRepository.findById(id)
                .map(set -> {
                    // Remove items currently in this set that are not in the new list
                    var currentItems = itemRepository.findBySetIdOrderBySetSortOrderAsc(id);
                    for (var item : currentItems) {
                        if (!orderedItemIds.contains(item.getId())) {
                            item.setSet(null);
                            item.setSetSortOrder(0);
                            itemRepository.save(item);
                        }
                    }

                    // Assign items to this set in the specified order
                    var allItems = itemRepository.findAllById(orderedItemIds);
                    for (int i = 0; i < orderedItemIds.size(); i++) {
                        Long targetId = orderedItemIds.get(i);
                        for (var item : allItems) {
                            if (item.getId().equals(targetId)) {
                                item.setSet(set);
                                item.setSetSortOrder(i);
                                break;
                            }
                        }
                    }
                    var saved = itemRepository.saveAll(allItems);
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/reorder")
    @Transactional
    public List<PortfolioSet> reorder(@RequestBody List<Long> orderedIds) {
        var allSets = setRepository.findAllById(orderedIds);
        for (int i = 0; i < orderedIds.size(); i++) {
            Long targetId = orderedIds.get(i);
            for (var set : allSets) {
                if (set.getId().equals(targetId)) {
                    set.setSortOrder(i);
                    break;
                }
            }
        }
        return setRepository.saveAll(allSets);
    }

    private void deleteIconAssets(PortfolioSet set) {
        deleteGcsUrl(set.getIconImageUrl());
        deleteGcsUrl(set.getIconOptimizedUrl());
        deleteGcsUrl(set.getIconThumbnailUrl());
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

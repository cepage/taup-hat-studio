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
@RequestMapping("/api/webcomic/series")
public class WebcomicSeriesController {

    private final WebcomicSeriesRepository seriesRepository;
    private final StorageService storageService;

    public WebcomicSeriesController(WebcomicSeriesRepository seriesRepository,
                                    StorageService storageService) {
        this.seriesRepository = seriesRepository;
        this.storageService = storageService;
    }

    @GetMapping
    public List<WebcomicSeries> listAll() {
        return seriesRepository.findAllByOrderBySortOrderAsc();
    }

    @GetMapping("/{id}")
    public ResponseEntity<WebcomicSeries> getById(@PathVariable Long id) {
        return seriesRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<WebcomicSeries> create(@RequestBody WebcomicSeries series) {
        series.setId(null);
        var saved = seriesRepository.save(series);
        return ResponseEntity
                .created(URI.create("/api/webcomic/series/" + saved.getId()))
                .body(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<WebcomicSeries> update(@PathVariable Long id, @RequestBody WebcomicSeries series) {
        return seriesRepository.findById(id)
                .map(existing -> {
                    existing.setTitle(series.getTitle());
                    existing.setSlug(series.getSlug());
                    existing.setDescription(series.getDescription());
                    existing.setCoverImageUrl(series.getCoverImageUrl());
                    existing.setSortOrder(series.getSortOrder());
                    existing.setActive(series.getActive());
                    return ResponseEntity.ok(seriesRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!seriesRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        // Delete all GCS assets for this series and its issues
        storageService.deleteByPrefix("images/webcomic/" + id + "/");
        seriesRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/reorder")
    public List<WebcomicSeries> reorder(@RequestBody List<Long> orderedIds) {
        var allSeries = seriesRepository.findAllById(orderedIds);
        for (int i = 0; i < orderedIds.size(); i++) {
            Long targetId = orderedIds.get(i);
            for (var s : allSeries) {
                if (s.getId().equals(targetId)) {
                    s.setSortOrder(i);
                    break;
                }
            }
        }
        return seriesRepository.saveAll(allSeries);
    }
}

package org.tanzu.thstudio.webcomic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WebcomicSeriesRepository extends JpaRepository<WebcomicSeries, Long> {

    List<WebcomicSeries> findAllByOrderBySortOrderAsc();

    List<WebcomicSeries> findByActiveTrueOrderBySortOrderAsc();

    Optional<WebcomicSeries> findBySlug(String slug);
}

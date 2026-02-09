package org.tanzu.thstudio.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortfolioItemRepository extends JpaRepository<PortfolioItem, Long> {

    List<PortfolioItem> findAllByOrderBySortOrderAsc();

    List<PortfolioItem> findByCategoryOrderBySortOrderAsc(String category);
}

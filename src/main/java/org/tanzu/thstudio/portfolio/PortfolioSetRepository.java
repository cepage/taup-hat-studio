package org.tanzu.thstudio.portfolio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortfolioSetRepository extends JpaRepository<PortfolioSet, Long> {

    List<PortfolioSet> findAllByOrderBySortOrderAsc();
}

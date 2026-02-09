package org.tanzu.thstudio.webcomic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebcomicPageRepository extends JpaRepository<WebcomicPage, Long> {

    List<WebcomicPage> findByIssueIdOrderByPageNumberAsc(Long issueId);
}

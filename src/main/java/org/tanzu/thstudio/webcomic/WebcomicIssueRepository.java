package org.tanzu.thstudio.webcomic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WebcomicIssueRepository extends JpaRepository<WebcomicIssue, Long> {

    List<WebcomicIssue> findBySeries_IdOrderByIssueNumberDesc(Long seriesId);

    List<WebcomicIssue> findBySeries_IdAndPublishedTrueOrderByIssueNumberDesc(Long seriesId);

    Optional<WebcomicIssue> findBySeries_IdAndIssueNumber(Long seriesId, Integer issueNumber);

    Optional<WebcomicIssue> findFirstBySeries_IdAndPublishedTrueOrderByIssueNumberDesc(Long seriesId);
}

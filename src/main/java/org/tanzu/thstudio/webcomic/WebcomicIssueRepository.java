package org.tanzu.thstudio.webcomic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WebcomicIssueRepository extends JpaRepository<WebcomicIssue, Long> {

    List<WebcomicIssue> findBySeriesIdOrderByIssueNumberDesc(Long seriesId);

    List<WebcomicIssue> findBySeriesIdAndPublishedTrueOrderByIssueNumberDesc(Long seriesId);

    Optional<WebcomicIssue> findBySeriesIdAndIssueNumber(Long seriesId, Integer issueNumber);

    Optional<WebcomicIssue> findFirstBySeriesIdAndPublishedTrueOrderByIssueNumberDesc(Long seriesId);
}

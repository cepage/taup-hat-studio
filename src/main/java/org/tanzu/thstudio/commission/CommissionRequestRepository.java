package org.tanzu.thstudio.commission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CommissionRequestRepository extends JpaRepository<CommissionRequest, Long> {

    boolean existsByFirestoreId(String firestoreId);

    List<CommissionRequest> findByStatusInAndAttemptsLessThanOrderByReceivedAtAsc(
            Collection<CommissionStatus> statuses, int maxAttempts);

    List<CommissionRequest> findAllByOrderByReceivedAtDesc();
}

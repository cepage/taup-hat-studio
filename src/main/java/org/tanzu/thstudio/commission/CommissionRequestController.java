package org.tanzu.thstudio.commission;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/commissions")
public class CommissionRequestController {

    private final CommissionRequestRepository repository;

    public CommissionRequestController(CommissionRequestRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<CommissionRequest> listAll() {
        return repository.findAllByOrderByReceivedAtDesc();
    }

    /** Re-queues a request for delivery on the next poll. */
    @PostMapping("/{id}/retry")
    public ResponseEntity<CommissionRequest> retry(@PathVariable Long id) {
        return repository.findById(id)
                .map(request -> {
                    request.setStatus(CommissionStatus.PENDING);
                    request.setAttempts(0);
                    request.setLastError(null);
                    return ResponseEntity.ok(repository.save(request));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** The Firestore copy is already gone once a request is ingested, so this removes it for good. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}

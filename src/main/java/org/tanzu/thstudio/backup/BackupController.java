package org.tanzu.thstudio.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/backup")
public class BackupController {

    private static final Logger log = LoggerFactory.getLogger(BackupController.class);

    private final BackupService backupService;

    public BackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> backup() {
        log.info("Manual backup requested");
        try {
            BackupResult result = backupService.performBackup();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "gcsPath", result.gcsPath(),
                    "compressedSizeBytes", result.compressedSizeBytes(),
                    "retainedCount", result.retainedCount(),
                    "timestamp", LocalDateTime.now().toString()
            ));
        } catch (Exception e) {
            log.error("Manual backup failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage() != null ? e.getMessage() : "Unknown error",
                    "timestamp", LocalDateTime.now().toString()
            ));
        }
    }
}

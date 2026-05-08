package org.tanzu.thstudio.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BackupScheduler {

    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);

    private final BackupService backupService;

    public BackupScheduler(BackupService backupService) {
        this.backupService = backupService;
    }

    @Scheduled(cron = "${tauphat.backup.schedule:0 0 2 * * SUN}", zone = "UTC")
    public void scheduledBackup() {
        try {
            backupService.performBackup();
        } catch (Exception e) {
            log.error("Scheduled backup failed", e);
        }
    }
}

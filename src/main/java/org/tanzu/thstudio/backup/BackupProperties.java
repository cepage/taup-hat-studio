package org.tanzu.thstudio.backup;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tauphat.backup")
public record BackupProperties(String schedule, int retentionCount) {
    public BackupProperties {
        if (schedule == null || schedule.isBlank()) schedule = "0 0 2 * * SUN";
        if (retentionCount <= 0) retentionCount = 3;
    }
}

package org.tanzu.thstudio.commission;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("tauphat.commissions")
public record CommissionProperties(boolean enabled, Duration pollInterval, int maxAttempts, String from) {
    public CommissionProperties {
        // enabled defaults to false
        if (pollInterval == null) pollInterval = Duration.ofMinutes(1);
        if (maxAttempts <= 0) maxAttempts = 5;
        if (from == null) from = "";
    }
}

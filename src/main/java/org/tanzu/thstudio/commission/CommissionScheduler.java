package org.tanzu.thstudio.commission;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CommissionScheduler {

    private static final Logger log = LoggerFactory.getLogger(CommissionScheduler.class);

    private final CommissionMailService mailService;
    private final CommissionProperties properties;

    public CommissionScheduler(CommissionMailService mailService, CommissionProperties properties) {
        this.mailService = mailService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${tauphat.commissions.poll-interval:PT1M}", initialDelayString = "PT30S")
    public void poll() {
        if (!properties.enabled()) return;
        try {
            mailService.ingest();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (Exception e) {
            log.error("Failed to drain the Firestore mail queue", e);
        }
        try {
            mailService.dispatch();
        } catch (Exception e) {
            log.error("Failed to dispatch commission requests", e);
        }
    }
}

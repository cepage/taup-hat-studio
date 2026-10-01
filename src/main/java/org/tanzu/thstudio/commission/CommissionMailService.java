package org.tanzu.thstudio.commission;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.tanzu.thstudio.site.SiteConfigService;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Drains commission requests from the Firestore {@code mail} queue into Postgres, then
 * delivers pending requests to the site's commissions address via SMTP.
 */
@Service
public class CommissionMailService {

    private static final Logger log = LoggerFactory.getLogger(CommissionMailService.class);
    private static final String EXTENSION_SUCCESS = "SUCCESS";

    private final FirestoreMailQueue queue;
    private final CommissionRequestRepository repository;
    private final JavaMailSender mailSender;
    private final SiteConfigService siteConfigService;
    private final CommissionProperties properties;

    public CommissionMailService(FirestoreMailQueue queue, CommissionRequestRepository repository,
                                 JavaMailSender mailSender, SiteConfigService siteConfigService,
                                 CommissionProperties properties) {
        this.queue = queue;
        this.repository = repository;
        this.mailSender = mailSender;
        this.siteConfigService = siteConfigService;
        this.properties = properties;
    }

    /**
     * Copies new queue documents into the database and removes them from Firestore.
     * Documents the retired Trigger Email extension already delivered are recorded as SENT.
     */
    public void ingest() throws IOException, InterruptedException {
        for (var mail : queue.fetch()) {
            if (!repository.existsByFirestoreId(mail.id())) {
                var request = new CommissionRequest();
                request.setFirestoreId(mail.id());
                request.setReplyTo(truncate(mail.replyTo(), 255));
                request.setSubject(truncate(mail.subject(), 255));
                request.setBody(mail.text());
                request.setReceivedAt(mail.createTime());
                if (EXTENSION_SUCCESS.equals(mail.deliveryState())) {
                    request.setStatus(CommissionStatus.SENT);
                    request.setSentAt(mail.createTime());
                }
                repository.save(request);
                log.info("Ingested commission request {} ({})", mail.id(), request.getStatus());
            }
            queue.delete(mail.id());
        }
    }

    /** Sends every pending or failed request that still has attempts remaining. */
    public void dispatch() {
        var due = repository.findByStatusInAndAttemptsLessThanOrderByReceivedAtAsc(
                List.of(CommissionStatus.PENDING, CommissionStatus.FAILED), properties.maxAttempts());
        if (due.isEmpty()) return;

        String recipient = siteConfigService.getConfig().getCommissionsEmail();
        if (recipient == null || recipient.isBlank()) {
            log.warn("{} commission request(s) waiting, but no commissions email is configured", due.size());
            return;
        }

        for (var request : due) {
            request.setAttempts(request.getAttempts() + 1);
            try {
                mailSender.send(toMessage(request, recipient));
                request.setStatus(CommissionStatus.SENT);
                request.setSentAt(Instant.now());
                request.setLastError(null);
                log.info("Sent commission request {}", request.getId());
            } catch (Exception e) {
                request.setStatus(CommissionStatus.FAILED);
                request.setLastError(e.getMessage());
                log.warn("Failed to send commission request {} (attempt {})", request.getId(), request.getAttempts(), e);
            }
            repository.save(request);
        }
    }

    private SimpleMailMessage toMessage(CommissionRequest request, String recipient) {
        var message = new SimpleMailMessage();
        if (!properties.from().isBlank()) message.setFrom(properties.from());
        message.setTo(recipient);
        if (isEmailAddress(request.getReplyTo())) message.setReplyTo(request.getReplyTo());
        message.setSubject(request.getSubject() != null ? request.getSubject() : "Commission request");
        message.setText(request.getBody() != null ? request.getBody() : "");
        return message;
    }

    static boolean isEmailAddress(String value) {
        if (value == null || !value.contains("@")) return false;
        try {
            new InternetAddress(value, true).validate();
            return true;
        } catch (AddressException e) {
            return false;
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}

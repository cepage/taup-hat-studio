package org.tanzu.thstudio.commission;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.tanzu.thstudio.commission.FirestoreMailQueue.QueuedMail;
import org.tanzu.thstudio.site.SiteConfigService;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("local")
class CommissionMailServiceTest {

    private static final Instant CREATED = Instant.parse("2026-09-30T12:00:00Z");

    @MockitoBean
    FirestoreMailQueue queue;

    @MockitoBean
    JavaMailSender mailSender;

    @Autowired
    CommissionMailService service;

    @Autowired
    CommissionRequestRepository repository;

    @Autowired
    SiteConfigService siteConfigService;

    @Autowired
    CommissionProperties properties;

    @Autowired
    CommissionRequestController controller;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        var config = siteConfigService.getConfig();
        config.setCommissionsEmail("owner@example.com");
        siteConfigService.save(config);
    }

    @Test
    void ingestStoresNewDocumentsOnceAndDeletesThemFromFirestore() throws Exception {
        var mail = new QueuedMail("doc1", "visitor@example.com", "Commission request — Ada", "Body", null, CREATED);
        when(queue.fetch()).thenReturn(List.of(mail));

        service.ingest();
        service.ingest(); // e.g. the delete failed last time and the doc is still in the queue

        assertThat(repository.findAll()).singleElement().satisfies(request -> {
            assertThat(request.getFirestoreId()).isEqualTo("doc1");
            assertThat(request.getStatus()).isEqualTo(CommissionStatus.PENDING);
            assertThat(request.getReceivedAt()).isEqualTo(CREATED);
        });
        verify(queue, times(2)).delete("doc1");
    }

    @Test
    void documentsAlreadyDeliveredByTheExtensionAreImportedAsSentAndNotResent() throws Exception {
        when(queue.fetch()).thenReturn(List.of(
                new QueuedMail("old1", "visitor@example.com", "Old", "Body", "SUCCESS", CREATED)));

        service.ingest();
        service.dispatch();

        assertThat(repository.findAll()).singleElement()
                .extracting(CommissionRequest::getStatus).isEqualTo(CommissionStatus.SENT);
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void dispatchSendsToTheConfiguredRecipientWithVisitorReplyTo() throws Exception {
        when(queue.fetch()).thenReturn(List.of(
                new QueuedMail("doc1", "visitor@example.com", "Commission request — Ada", "Body", null, CREATED)));
        service.ingest();

        service.dispatch();

        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        var message = captor.getValue();
        assertThat(message.getTo()).containsExactly("owner@example.com");
        assertThat(message.getReplyTo()).isEqualTo("visitor@example.com");
        assertThat(message.getSubject()).isEqualTo("Commission request — Ada");
        assertThat(message.getText()).isEqualTo("Body");

        var stored = repository.findAll().getFirst();
        assertThat(stored.getStatus()).isEqualTo(CommissionStatus.SENT);
        assertThat(stored.getSentAt()).isNotNull();
    }

    @Test
    void nonEmailContactIsNotUsedAsReplyTo() throws Exception {
        when(queue.fetch()).thenReturn(List.of(
                new QueuedMail("doc1", "@ada on Instagram", "Subject", "Body", null, CREATED)));
        service.ingest();

        service.dispatch();

        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getReplyTo()).isNull();
    }

    @Test
    void deletedRequestsAreRemovedAndNotSent() throws Exception {
        when(queue.fetch()).thenReturn(List.of(
                new QueuedMail("doc1", "visitor@example.com", "Subject", "Body", null, CREATED)));
        service.ingest();
        Long id = repository.findAll().getFirst().getId();

        assertThat(controller.delete(id).getStatusCode().value()).isEqualTo(204);
        assertThat(controller.delete(id).getStatusCode().value()).isEqualTo(404);
        service.dispatch();

        assertThat(repository.findAll()).isEmpty();
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void failedSendsAreRetriedUntilMaxAttempts() throws Exception {
        when(queue.fetch()).thenReturn(List.of(
                new QueuedMail("doc1", "visitor@example.com", "Subject", "Body", null, CREATED)));
        service.ingest();
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        for (int i = 0; i < properties.maxAttempts() + 2; i++) {
            service.dispatch();
        }

        verify(mailSender, times(properties.maxAttempts())).send(any(SimpleMailMessage.class));
        var stored = repository.findAll().getFirst();
        assertThat(stored.getStatus()).isEqualTo(CommissionStatus.FAILED);
        assertThat(stored.getAttempts()).isEqualTo(properties.maxAttempts());
        assertThat(stored.getLastError()).contains("SMTP down");

        reset(mailSender);
        service.dispatch();
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }
}

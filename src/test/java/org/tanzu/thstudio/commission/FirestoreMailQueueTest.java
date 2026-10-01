package org.tanzu.thstudio.commission;

import org.junit.jupiter.api.Test;
import org.tanzu.thstudio.config.TaupHatProperties;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FirestoreMailQueueTest {

    private final FirestoreMailQueue queue = new FirestoreMailQueue(
            new TaupHatProperties(null, null, null));

    @Test
    void parsesQueuedAndDeliveredDocuments() {
        String json = """
                {
                  "documents": [
                    {
                      "name": "projects/p/databases/(default)/documents/mail/abc123",
                      "fields": {
                        "to": { "stringValue": "owner@example.com" },
                        "replyTo": { "stringValue": "visitor@example.com" },
                        "message": { "mapValue": { "fields": {
                          "subject": { "stringValue": "Commission request — Ada" },
                          "text": { "stringValue": "Name: Ada\\nBudget: $100" }
                        } } }
                      },
                      "createTime": "2026-09-30T12:00:00.123456Z",
                      "updateTime": "2026-09-30T12:00:00.123456Z"
                    },
                    {
                      "name": "projects/p/databases/(default)/documents/mail/old1",
                      "fields": {
                        "replyTo": { "stringValue": "@someone" },
                        "message": { "mapValue": { "fields": {
                          "subject": { "stringValue": "Old" },
                          "text": { "stringValue": "Old body" }
                        } } },
                        "delivery": { "mapValue": { "fields": {
                          "state": { "stringValue": "SUCCESS" }
                        } } }
                      },
                      "createTime": "2026-01-01T00:00:00Z"
                    }
                  ]
                }
                """;

        var mails = queue.parse(json);

        assertThat(mails).hasSize(2);
        var first = mails.getFirst();
        assertThat(first.id()).isEqualTo("abc123");
        assertThat(first.replyTo()).isEqualTo("visitor@example.com");
        assertThat(first.subject()).isEqualTo("Commission request — Ada");
        assertThat(first.text()).isEqualTo("Name: Ada\nBudget: $100");
        assertThat(first.deliveryState()).isNull();
        assertThat(first.createTime()).isEqualTo(Instant.parse("2026-09-30T12:00:00.123456Z"));
        assertThat(mails.get(1).deliveryState()).isEqualTo("SUCCESS");
    }

    @Test
    void parsesEmptyCollection() {
        assertThat(queue.parse("{}")).isEmpty();
    }
}

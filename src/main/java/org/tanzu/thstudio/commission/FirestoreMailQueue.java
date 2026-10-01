package org.tanzu.thstudio.commission;

import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.stereotype.Component;
import org.tanzu.thstudio.config.TaupHatProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and deletes documents in the Firestore {@code mail} collection, which the static
 * site's commissions form writes to. Uses the Firestore REST API with Application Default
 * Credentials rather than the gRPC client library to keep the CMS footprint small.
 */
@Component
public class FirestoreMailQueue {

    private static final String BASE_URL = "https://firestore.googleapis.com/v1";
    private static final String DATASTORE_SCOPE = "https://www.googleapis.com/auth/datastore";
    private static final int PAGE_SIZE = 50;

    /** A queued {@code mail} document. {@code deliveryState} is set only if the old extension processed it. */
    public record QueuedMail(String id, String replyTo, String subject, String text,
                             String deliveryState, Instant createTime) {
    }

    private final TaupHatProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public FirestoreMailQueue(TaupHatProperties properties) {
        this.properties = properties;
    }

    public List<QueuedMail> fetch() throws IOException, InterruptedException {
        var request = authorized(URI.create(collectionUrl() + "?pageSize=" + PAGE_SIZE)).GET().build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Firestore list failed: " + response.statusCode() + " " + response.body());
        }
        return parse(response.body());
    }

    public void delete(String id) throws IOException, InterruptedException {
        var request = authorized(URI.create(collectionUrl() + "/" + id)).DELETE().build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Firestore delete failed: " + response.statusCode() + " " + response.body());
        }
    }

    List<QueuedMail> parse(String body) {
        var documents = objectMapper.readTree(body).path("documents");
        var result = new ArrayList<QueuedMail>();
        for (JsonNode doc : documents) {
            String name = doc.path("name").asText();
            var fields = doc.path("fields");
            var message = fields.path("message").path("mapValue").path("fields");
            var delivery = fields.path("delivery").path("mapValue").path("fields");
            result.add(new QueuedMail(
                    name.substring(name.lastIndexOf('/') + 1),
                    stringField(fields, "replyTo"),
                    stringField(message, "subject"),
                    stringField(message, "text"),
                    stringField(delivery, "state"),
                    doc.has("createTime") ? Instant.parse(doc.path("createTime").asText()) : Instant.now()));
        }
        return result;
    }

    private static String stringField(JsonNode fields, String key) {
        var value = fields.path(key).path("stringValue");
        return value.isMissingNode() ? null : value.asText();
    }

    private String collectionUrl() {
        String projectId = properties.firebase().projectId();
        if (projectId.isBlank()) {
            throw new IllegalStateException("tauphat.firebase.project-id is not configured");
        }
        return BASE_URL + "/projects/" + projectId + "/databases/(default)/documents/mail";
    }

    private HttpRequest.Builder authorized(URI uri) throws IOException {
        var credentials = GoogleCredentials.getApplicationDefault().createScoped(DATASTORE_SCOPE);
        credentials.refreshIfExpired();
        return HttpRequest.newBuilder(uri)
                .header("Authorization", "Bearer " + credentials.getAccessToken().getTokenValue());
    }
}

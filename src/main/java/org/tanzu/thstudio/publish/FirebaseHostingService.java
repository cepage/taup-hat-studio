package org.tanzu.thstudio.publish;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.tanzu.thstudio.config.TaupHatProperties;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Deploys a {@link GeneratedSite} to Firebase Hosting via the REST API.
 * <p>
 * Supports deploying to both preview channels (temporary URLs for staging)
 * and the live channel (production).
 * <p>
 * Authentication uses Application Default Credentials with the
 * {@code firebase.hosting} scope.
 */
@Service
public class FirebaseHostingService {

    private static final Logger log = LoggerFactory.getLogger(FirebaseHostingService.class);
    private static final String BASE_URL = "https://firebasehosting.googleapis.com/v1beta1";
    private static final String HOSTING_SCOPE = "https://www.googleapis.com/auth/firebase.hosting";

    private final TaupHatProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public FirebaseHostingService(TaupHatProperties properties) {
        this.properties = properties;
    }

    /**
     * Deploys the site to a Firebase preview channel and returns the preview URL.
     *
     * @param site the generated static site
     * @return the preview channel URL (e.g. {@code https://site--preview-abc123.web.app})
     */
    public String deployToPreview(GeneratedSite site) throws IOException, InterruptedException {
        validateConfig();
        String siteId = properties.firebase().siteId();
        log.info("Deploying {} files to Firebase preview channel for site '{}'", site.fileCount(), siteId);

        // Prepare gzipped content and hashes
        var fileHashes = prepareFiles(site);

        // Create version
        String versionId = createVersion(siteId);
        log.info("Created version: {}", versionId);

        // Populate files and upload required ones
        var uploadUrl = populateFiles(versionId, fileHashes);
        uploadFiles(uploadUrl, fileHashes);

        // Finalize version
        finalizeVersion(versionId);
        log.info("Version finalized: {}", versionId);

        // Create preview channel and release
        String previewUrl = createPreviewRelease(siteId, versionId);
        log.info("Preview deployed: {}", previewUrl);

        return previewUrl;
    }

    /**
     * Deploys the site to the live Firebase Hosting channel.
     *
     * @param site the generated static site
     */
    public void deployToLive(GeneratedSite site) throws IOException, InterruptedException {
        validateConfig();
        String siteId = properties.firebase().siteId();
        log.info("Deploying {} files to Firebase live channel for site '{}'", site.fileCount(), siteId);

        var fileHashes = prepareFiles(site);

        String versionId = createVersion(siteId);
        log.info("Created version: {}", versionId);

        var uploadUrl = populateFiles(versionId, fileHashes);
        uploadFiles(uploadUrl, fileHashes);

        finalizeVersion(versionId);
        log.info("Version finalized: {}", versionId);

        createLiveRelease(siteId, versionId);
        log.info("Live deployment complete for site '{}'", siteId);
    }

    // ── Firebase Hosting REST API steps ─────────────────────────────────────

    /**
     * Step 1: Create a new version for the site.
     * Returns the full version name (e.g. "sites/my-site/versions/abc123").
     */
    private String createVersion(String siteId) throws IOException, InterruptedException {
        var configMap = Map.of(
                "config", Map.of(
                        "headers", new Object[]{
                                Map.of(
                                        "glob", "**/*.html",
                                        "headers", Map.of("Cache-Control", "no-cache")
                                ),
                                Map.of(
                                        "glob", "**/*.{css,js}",
                                        "headers", Map.of("Cache-Control", "public, max-age=3600")
                                )
                        }
                )
        );

        var response = firebasePost(
                BASE_URL + "/sites/" + siteId + "/versions",
                objectMapper.writeValueAsString(configMap)
        );
        var json = objectMapper.readTree(response);
        return json.get("name").asText();
    }

    /**
     * Step 2: Populate files in the version.
     * Sends a map of {"/path": "gzipSha256Hash"} and receives the upload URL
     * plus which files need to be uploaded.
     * Returns the upload URL prefix.
     */
    private String populateFiles(String versionName, Map<String, FileData> fileHashes)
            throws IOException, InterruptedException {
        var filesMap = new LinkedHashMap<String, String>();
        for (var entry : fileHashes.entrySet()) {
            // Firebase expects paths starting with /
            String path = entry.getKey().startsWith("/") ? entry.getKey() : "/" + entry.getKey();
            filesMap.put(path, entry.getValue().hash());
        }

        var body = Map.of("files", filesMap);
        var response = firebasePost(
                BASE_URL + "/" + versionName + ":populateFiles",
                objectMapper.writeValueAsString(body)
        );
        var json = objectMapper.readTree(response);

        String uploadUrl = json.has("uploadUrl") ? json.get("uploadUrl").asText() : "";
        int requiredCount = json.has("uploadRequiredHashes")
                ? json.get("uploadRequiredHashes").size()
                : 0;
        log.info("Firebase requires {} file uploads", requiredCount);

        return uploadUrl;
    }

    /**
     * Step 3: Upload gzipped file bytes for each required hash.
     * Firebase expects: POST {uploadUrl}/{hash} with the gzipped content.
     */
    private void uploadFiles(String uploadUrl, Map<String, FileData> fileHashes)
            throws IOException, InterruptedException {
        if (uploadUrl == null || uploadUrl.isBlank()) {
            log.info("No files need uploading (all already cached by Firebase)");
            return;
        }

        String accessToken = getAccessToken();
        int uploaded = 0;

        for (var entry : fileHashes.entrySet()) {
            var fileData = entry.getValue();
            String url = uploadUrl + "/" + fileData.hash();

            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/octet-stream")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(fileData.gzipped()))
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("Upload failed for {}: {} {}", entry.getKey(), response.statusCode(), response.body());
            } else {
                uploaded++;
            }
        }
        log.info("Uploaded {} files to Firebase", uploaded);
    }

    /**
     * Step 4a: Finalize the version (set status to FINALIZED).
     */
    private void finalizeVersion(String versionName) throws IOException, InterruptedException {
        firebasePatch(
                BASE_URL + "/" + versionName + "?update_mask=status",
                "{\"status\": \"FINALIZED\"}"
        );
    }

    /**
     * Step 4b (preview): Create a preview channel and release the version to it.
     * Returns the preview channel URL.
     */
    private String createPreviewRelease(String siteId, String versionName)
            throws IOException, InterruptedException {
        // Create or get the preview channel
        String channelId = "preview";
        String channelUrl = BASE_URL + "/sites/" + siteId + "/channels/" + channelId;

        // Try to create the channel (if it already exists, that's fine)
        try {
            var channelBody = Map.of(
                    "ttl", "604800s" // 7 days
            );
            firebasePost(
                    BASE_URL + "/sites/" + siteId + "/channels?channelId=" + channelId,
                    objectMapper.writeValueAsString(channelBody)
            );
        } catch (IOException e) {
            // Channel may already exist, which is fine
            log.debug("Preview channel may already exist: {}", e.getMessage());
        }

        // Create a release on the preview channel
        var releaseBody = Map.of("message", "Preview from TaupHat Studio");
        firebasePost(
                channelUrl + "/releases?versionName=" + versionName,
                objectMapper.writeValueAsString(releaseBody)
        );

        // Extract the preview URL from the channel
        var channelResponse = firebaseGet(channelUrl);
        var channelJson = objectMapper.readTree(channelResponse);
        if (channelJson.has("url")) {
            return channelJson.get("url").asText();
        }

        // Fallback: construct URL from site ID
        return "https://" + siteId + "--" + channelId + ".web.app";
    }

    /**
     * Step 4b (live): Release the version to the live channel.
     */
    private void createLiveRelease(String siteId, String versionName)
            throws IOException, InterruptedException {
        var releaseBody = Map.of("message", "Deploy from TaupHat Studio");
        firebasePost(
                BASE_URL + "/sites/" + siteId + "/channels/live/releases?versionName=" + versionName,
                objectMapper.writeValueAsString(releaseBody)
        );
    }

    // ── File preparation ────────────────────────────────────────────────────

    /**
     * Gzips each file and computes SHA-256 of the gzipped content.
     * Returns a map from relative path to {@link FileData}.
     */
    private Map<String, FileData> prepareFiles(GeneratedSite site) {
        var result = new LinkedHashMap<String, FileData>();
        for (var entry : site.getFiles().entrySet()) {
            byte[] gzipped = gzip(entry.getValue().content());
            String hash = sha256Hex(gzipped);
            result.put(entry.getKey(), new FileData(gzipped, hash));
        }
        return result;
    }

    private record FileData(byte[] gzipped, String hash) {}

    // ── HTTP helpers ────────────────────────────────────────────────────────

    private String firebasePost(String url, String body) throws IOException, InterruptedException {
        String accessToken = getAccessToken();
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("Firebase API error (%d): %s".formatted(response.statusCode(), response.body()));
        }
        return response.body();
    }

    private String firebasePatch(String url, String body) throws IOException, InterruptedException {
        String accessToken = getAccessToken();
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("Firebase API error (%d): %s".formatted(response.statusCode(), response.body()));
        }
        return response.body();
    }

    private String firebaseGet(String url) throws IOException, InterruptedException {
        String accessToken = getAccessToken();
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("Firebase API error (%d): %s".formatted(response.statusCode(), response.body()));
        }
        return response.body();
    }

    private String getAccessToken() throws IOException {
        var credentials = GoogleCredentials.getApplicationDefault()
                .createScoped(HOSTING_SCOPE);
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }

    // ── Utility ─────────────────────────────────────────────────────────────

    private void validateConfig() {
        String siteId = properties.firebase().siteId();
        if (siteId == null || siteId.isBlank()) {
            throw new IllegalStateException(
                    "Firebase site ID is not configured. Set tauphat.firebase.site-id or FIREBASE_SITE_ID environment variable.");
        }
    }

    private static byte[] gzip(byte[] data) {
        try (var baos = new ByteArrayOutputStream();
             var gzip = new GZIPOutputStream(baos)) {
            gzip.write(data);
            gzip.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to gzip content", e);
        }
    }

    private static String sha256Hex(byte[] data) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}

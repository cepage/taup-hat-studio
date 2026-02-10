package org.tanzu.thstudio.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes Google service account credentials from the {@code GOOGLE_CREDENTIALS_JSON}
 * environment variable to the file path specified by {@code GOOGLE_APPLICATION_CREDENTIALS}.
 * <p>
 * This is necessary on Cloud Foundry because the platform can only inject environment
 * variables — not files — into the container. The Google Cloud client libraries expect
 * {@code GOOGLE_APPLICATION_CREDENTIALS} to point to a file on disk, so this component
 * bridges the gap by writing the JSON content to that location at startup.
 * <p>
 * Both environment variables are set in {@code manifest.yml}.
 */
@Component
public class GoogleCredentialsInitializer {

    private static final Logger log = LoggerFactory.getLogger(GoogleCredentialsInitializer.class);

    public GoogleCredentialsInitializer() {
        String credentialsJson = System.getenv("GOOGLE_CREDENTIALS_JSON");
        String credentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");

        if (credentialsJson == null || credentialsJson.isBlank()) {
            log.debug("GOOGLE_CREDENTIALS_JSON not set; skipping credentials file creation");
            return;
        }
        if (credentialsPath == null || credentialsPath.isBlank()) {
            log.debug("GOOGLE_APPLICATION_CREDENTIALS not set; skipping credentials file creation");
            return;
        }

        Path path = Path.of(credentialsPath);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, credentialsJson);
            log.info("Wrote Google credentials to {}", path);
        } catch (IOException e) {
            log.error("Failed to write Google credentials to {}", path, e);
        }
    }
}

package org.tanzu.thstudio.image;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.tanzu.thstudio.config.TaupHatProperties;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Service for uploading and managing files in Google Cloud Storage.
 * Initializes the GCS client lazily to avoid startup failures when credentials are unavailable.
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final TaupHatProperties properties;
    private volatile Storage storage;

    public StorageService(TaupHatProperties properties) {
        this.properties = properties;
    }

    private Storage getStorage() {
        if (storage == null) {
            synchronized (this) {
                if (storage == null) {
                    String projectId = properties.gcs().projectId();
                    if (projectId != null && !projectId.isBlank()) {
                        storage = StorageOptions.newBuilder()
                                .setProjectId(projectId)
                                .build()
                                .getService();
                    } else {
                        storage = StorageOptions.getDefaultInstance().getService();
                    }
                }
            }
        }
        return storage;
    }

    /**
     * Uploads a file to GCS and returns its public URL.
     *
     * @param path        the object path within the bucket (e.g. "images/originals/comic/1/page1.png")
     * @param content     the file content
     * @param contentType the MIME type
     * @return the public URL of the uploaded object
     */
    public String upload(String path, byte[] content, String contentType) {
        String bucket = properties.gcs().bucketName();
        BlobId blobId = BlobId.of(bucket, path);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(contentType)
                .build();
        getStorage().create(blobInfo, content);
        log.info("Uploaded {} to gs://{}/{}", contentType, bucket, path);
        return publicUrl(bucket, path);
    }

    /**
     * Uploads a file from an InputStream to GCS and returns its public URL.
     */
    public String upload(String path, InputStream content, String contentType) throws IOException {
        return upload(path, content.readAllBytes(), contentType);
    }

    /**
     * Uploads a file from a local Path to GCS, streaming its content to avoid
     * loading the entire file into heap or direct buffer memory.
     *
     * @param path        the object path within the bucket
     * @param sourceFile  local file to upload
     * @param contentType the MIME type
     * @return the public URL of the uploaded object
     */
    public String upload(String path, Path sourceFile, String contentType) throws IOException {
        String bucket = properties.gcs().bucketName();
        BlobId blobId = BlobId.of(bucket, path);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                .setContentType(contentType)
                .build();
        try (var channel = getStorage().writer(blobInfo);
             var inputStream = Files.newInputStream(sourceFile)) {
            byte[] buffer = new byte[64 * 1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                channel.write(ByteBuffer.wrap(buffer, 0, bytesRead));
            }
        }
        log.info("Uploaded {} to gs://{}/{} (streamed from disk)", contentType, bucket, path);
        return publicUrl(bucket, path);
    }

    /**
     * Deletes a single object from GCS.
     */
    public void delete(String path) {
        String bucket = properties.gcs().bucketName();
        getStorage().delete(BlobId.of(bucket, path));
        log.info("Deleted gs://{}/{}", bucket, path);
    }

    /**
     * Deletes all objects under the given prefix in GCS.
     * Useful for cleaning up all assets belonging to an issue or series.
     *
     * @param prefix the object path prefix (e.g. "images/webcomic/1/2")
     */
    public void deleteByPrefix(String prefix) {
        String bucket = properties.gcs().bucketName();
        var blobs = getStorage().list(bucket, Storage.BlobListOption.prefix(prefix));
        int count = 0;
        for (Blob blob : blobs.iterateAll()) {
            blob.delete();
            count++;
        }
        log.info("Deleted {} objects under gs://{}/{}", count, bucket, prefix);
    }

    private String publicUrl(String bucket, String path) {
        return "https://storage.googleapis.com/" + bucket + "/" + path;
    }
}

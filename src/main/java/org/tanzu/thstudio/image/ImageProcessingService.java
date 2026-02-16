package org.tanzu.thstudio.image;

import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Processes uploaded images into multiple size variants and stores them in GCS.
 * The original is stored in its native format. Resized variants are always output
 * as PNG to avoid codec compatibility issues (e.g. WebP write support).
 *
 * <p>All processing uses temporary files on disk to avoid holding large images
 * in direct buffer memory, which has a limited capacity on the JVM.</p>
 */
@Service
public class ImageProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ImageProcessingService.class);

    private static final int THUMBNAIL_WIDTH = 300;
    private static final int OPTIMIZED_WIDTH = 1200;
    private static final String RESIZED_FORMAT = "png";
    private static final String RESIZED_CONTENT_TYPE = "image/png";
    private static final String RESIZED_EXTENSION = ".png";

    private final StorageService storageService;

    public ImageProcessingService(StorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * Processes an uploaded image and stores original, optimized, and thumbnail variants.
     *
     * <p>The upload is streamed to a temporary file first to avoid loading the entire
     * image into direct buffer memory (which is capped by {@code -XX:MaxDirectMemorySize}).</p>
     *
     * @param file     the uploaded image file
     * @param basePath the base GCS path (e.g. "images/webcomic/1/1")
     * @param filename the base filename without extension (e.g. "page-001")
     * @return an ImageUrls record containing URLs for all three variants
     */
    public ImageUrls processAndUpload(MultipartFile file, String basePath, String filename) throws IOException {
        String contentType = file.getContentType();
        String extension = extensionFromContentType(contentType);

        // Stream upload to a temp file to avoid OOM on large images
        Path tempFile = Files.createTempFile("thstudio-upload-", extension);
        try {
            file.transferTo(tempFile);

            // Read original image dimensions for PhotoSwipe lightbox integration
            BufferedImage buffered = ImageIO.read(tempFile.toFile());
            int width = buffered != null ? buffered.getWidth() : 0;
            int height = buffered != null ? buffered.getHeight() : 0;

            // Upload original in its native format (streamed from disk)
            String originalPath = basePath + "/original/" + filename + extension;
            String originalUrl = storageService.upload(originalPath, tempFile, contentType);

            // Generate and upload optimized version (as PNG)
            byte[] optimized = resize(tempFile, OPTIMIZED_WIDTH);
            String optimizedPath = basePath + "/optimized/" + filename + RESIZED_EXTENSION;
            String optimizedUrl = storageService.upload(optimizedPath, optimized, RESIZED_CONTENT_TYPE);

            // Generate and upload thumbnail (as PNG)
            byte[] thumbnail = resize(tempFile, THUMBNAIL_WIDTH);
            String thumbnailPath = basePath + "/thumbnail/" + filename + RESIZED_EXTENSION;
            String thumbnailUrl = storageService.upload(thumbnailPath, thumbnail, RESIZED_CONTENT_TYPE);

            log.info("Processed image {} -> original ({}), optimized ({}px), thumbnail ({}px), dimensions {}x{}",
                    filename, contentType, OPTIMIZED_WIDTH, THUMBNAIL_WIDTH, width, height);

            return new ImageUrls(originalUrl, optimizedUrl, thumbnailUrl, width, height);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Resizes an image to fit within the given width while maintaining aspect ratio.
     * Reads from a file on disk to avoid direct buffer pressure.
     * Always outputs as PNG for broad format compatibility.
     */
    private byte[] resize(Path sourceFile, int maxWidth) throws IOException {
        var output = new ByteArrayOutputStream();
        Thumbnails.of(sourceFile.toFile())
                .width(maxWidth)
                .keepAspectRatio(true)
                .outputFormat(RESIZED_FORMAT)
                .outputQuality(0.85)
                .toOutputStream(output);
        return output.toByteArray();
    }

    private String extensionFromContentType(String contentType) {
        if (contentType == null) return ".png";
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".png";
        };
    }

    /**
     * URLs for the three image size variants, plus original image dimensions.
     */
    public record ImageUrls(String originalUrl, String optimizedUrl, String thumbnailUrl,
                            int width, int height) {
    }
}

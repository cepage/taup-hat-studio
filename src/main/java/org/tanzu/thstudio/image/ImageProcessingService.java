package org.tanzu.thstudio.image;

import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Processes uploaded images into multiple size variants and stores them in GCS.
 */
@Service
public class ImageProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ImageProcessingService.class);

    private static final int THUMBNAIL_WIDTH = 300;
    private static final int OPTIMIZED_WIDTH = 1200;

    private final StorageService storageService;

    public ImageProcessingService(StorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * Processes an uploaded image and stores original, optimized, and thumbnail variants.
     *
     * @param file     the uploaded image file
     * @param basePath the base GCS path (e.g. "images/webcomic/1/1")
     * @param filename the base filename without extension (e.g. "page-001")
     * @return an ImageUrls record containing URLs for all three variants
     */
    public ImageUrls processAndUpload(MultipartFile file, String basePath, String filename) throws IOException {
        String contentType = file.getContentType();
        String extension = extensionFromContentType(contentType);

        // Upload original
        String originalPath = basePath + "/original/" + filename + extension;
        String originalUrl = storageService.upload(originalPath, file.getBytes(), contentType);

        // Generate and upload optimized version
        byte[] optimized = resize(file, OPTIMIZED_WIDTH);
        String optimizedPath = basePath + "/optimized/" + filename + extension;
        String optimizedUrl = storageService.upload(optimizedPath, optimized, contentType);

        // Generate and upload thumbnail
        byte[] thumbnail = resize(file, THUMBNAIL_WIDTH);
        String thumbnailPath = basePath + "/thumbnail/" + filename + extension;
        String thumbnailUrl = storageService.upload(thumbnailPath, thumbnail, contentType);

        log.info("Processed image {} -> original, optimized ({}px), thumbnail ({}px)",
                filename, OPTIMIZED_WIDTH, THUMBNAIL_WIDTH);

        return new ImageUrls(originalUrl, optimizedUrl, thumbnailUrl);
    }

    /**
     * Resizes an image to fit within the given width while maintaining aspect ratio.
     * If the image is already smaller than the target width, it is returned as-is.
     */
    private byte[] resize(MultipartFile file, int maxWidth) throws IOException {
        var output = new ByteArrayOutputStream();
        Thumbnails.of(file.getInputStream())
                .width(maxWidth)
                .keepAspectRatio(true)
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
     * URLs for the three image size variants.
     */
    public record ImageUrls(String originalUrl, String optimizedUrl, String thumbnailUrl) {
    }
}

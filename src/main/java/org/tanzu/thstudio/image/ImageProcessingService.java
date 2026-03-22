package org.tanzu.thstudio.image;

import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * Processes uploaded images into multiple size variants and stores them in GCS.
 * The original is stored in its native format. Resized variants are always output
 * as PNG to avoid codec compatibility issues (e.g. WebP write support).
 *
 * <p>Processing is designed for memory efficiency: the image is decoded into a
 * {@link BufferedImage} exactly once, and both resized variants are generated from
 * that single copy. Image dimensions are read from metadata without decoding pixels.
 * The original file is streamed to GCS from a temporary file on disk.</p>
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
     * image into direct buffer memory (which is capped by {@code -XX:MaxDirectMemorySize}).
     * Pixel data is decoded only once for both resize operations to minimize heap usage.</p>
     *
     * @param file     the uploaded image file
     * @param basePath the base GCS path (e.g. "images/webcomic/1/1")
     * @param filename the base filename without extension (e.g. "page-001")
     * @return an ImageUrls record containing URLs for all three variants
     */
    public ImageUrls processAndUpload(MultipartFile file, String basePath, String filename) throws IOException {
        String contentType = file.getContentType();
        String extension = extensionFromContentType(contentType);

        Path tempFile = Files.createTempFile("thstudio-upload-", extension);
        try {
            file.transferTo(tempFile);

            // Read dimensions from image metadata without decoding pixel data
            var dimensions = readDimensions(tempFile);

            // Upload original in its native format (streamed from disk)
            String originalPath = basePath + "/original/" + filename + extension;
            String originalUrl = storageService.upload(originalPath, tempFile, contentType);

            // Decode the image once and generate both resized variants from memory
            BufferedImage decoded = ImageIO.read(tempFile.toFile());

            byte[] optimized = resize(decoded, OPTIMIZED_WIDTH);
            String optimizedPath = basePath + "/optimized/" + filename + RESIZED_EXTENSION;
            String optimizedUrl = storageService.upload(optimizedPath, optimized, RESIZED_CONTENT_TYPE);

            byte[] thumbnail = resize(decoded, THUMBNAIL_WIDTH);
            String thumbnailPath = basePath + "/thumbnail/" + filename + RESIZED_EXTENSION;
            String thumbnailUrl = storageService.upload(thumbnailPath, thumbnail, RESIZED_CONTENT_TYPE);

            // Allow the large decoded image to be GC'd immediately
            decoded = null;

            log.info("Processed image {} -> original ({}), optimized ({}px), thumbnail ({}px), dimensions {}x{}",
                    filename, contentType, OPTIMIZED_WIDTH, THUMBNAIL_WIDTH,
                    dimensions.width(), dimensions.height());

            return new ImageUrls(originalUrl, optimizedUrl, thumbnailUrl,
                    dimensions.width(), dimensions.height());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Reads image dimensions from file metadata without decoding the full pixel data.
     * This avoids allocating a ~(width * height * 4) byte BufferedImage just for dimensions.
     */
    private ImageDimensions readDimensions(Path imageFile) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(imageFile.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (readers.hasNext()) {
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input);
                    return new ImageDimensions(reader.getWidth(0), reader.getHeight(0));
                } finally {
                    reader.dispose();
                }
            }
        }
        return new ImageDimensions(0, 0);
    }

    /**
     * Resizes a decoded image to fit within the given width while maintaining aspect ratio.
     * Accepts a BufferedImage to avoid re-decoding the source file from disk.
     * Always outputs as PNG for broad format compatibility.
     */
    private byte[] resize(BufferedImage source, int maxWidth) throws IOException {
        var output = new ByteArrayOutputStream();
        Thumbnails.of(source)
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

    private record ImageDimensions(int width, int height) {
    }

    /**
     * URLs for the three image size variants, plus original image dimensions.
     */
    public record ImageUrls(String originalUrl, String optimizedUrl, String thumbnailUrl,
                            int width, int height) {
    }
}

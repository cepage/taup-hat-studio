package org.tanzu.thstudio.publish;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds all files generated for the static site as a map from
 * relative path (e.g. "index.html", "comics/index.html") to file content.
 */
public class GeneratedSite {

    private final Map<String, FileEntry> files = new LinkedHashMap<>();

    public void addHtml(String path, String content) {
        files.put(path, new FileEntry(content.getBytes(java.nio.charset.StandardCharsets.UTF_8), "text/html; charset=utf-8"));
    }

    public void addCss(String path, String content) {
        files.put(path, new FileEntry(content.getBytes(java.nio.charset.StandardCharsets.UTF_8), "text/css; charset=utf-8"));
    }

    public void addJs(String path, String content) {
        files.put(path, new FileEntry(content.getBytes(java.nio.charset.StandardCharsets.UTF_8), "application/javascript; charset=utf-8"));
    }

    public Map<String, FileEntry> getFiles() {
        return files;
    }

    public int fileCount() {
        return files.size();
    }

    public record FileEntry(byte[] content, String contentType) {}
}

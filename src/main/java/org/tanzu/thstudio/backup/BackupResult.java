package org.tanzu.thstudio.backup;

public record BackupResult(String gcsPath, long compressedSizeBytes, int retainedCount) {
}

package org.tanzu.thstudio.backup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.tanzu.thstudio.image.StorageService;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter FILENAME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm");

    private final DataSource dataSource;
    private final SqlDumpGenerator sqlDumpGenerator;
    private final StorageService storageService;
    private final BackupProperties backupProperties;

    public BackupService(DataSource dataSource, SqlDumpGenerator sqlDumpGenerator,
                         StorageService storageService, BackupProperties backupProperties) {
        this.dataSource = dataSource;
        this.sqlDumpGenerator = sqlDumpGenerator;
        this.storageService = storageService;
        this.backupProperties = backupProperties;
    }

    public BackupResult performBackup() throws Exception {
        String path = "backups/" + LocalDateTime.now().format(FILENAME_FORMAT) + ".sql.gz";
        log.info("Starting database backup to {}", path);

        byte[] bytes;
        try (var connection = dataSource.getConnection()) {
            bytes = sqlDumpGenerator.dump(connection);
        }

        storageService.upload(path, bytes, "application/gzip");
        int retained = enforceRetention();
        log.info("Backup complete: {} bytes compressed, {} backups retained", bytes.length, retained);
        return new BackupResult(path, bytes.length, retained);
    }

    private int enforceRetention() {
        List<String> all = storageService.listByPrefix("backups/").stream().sorted().toList();
        int excess = all.size() - backupProperties.retentionCount();
        if (excess > 0) all.subList(0, excess).forEach(storageService::delete);
        return all.size() - Math.max(0, excess);
    }
}

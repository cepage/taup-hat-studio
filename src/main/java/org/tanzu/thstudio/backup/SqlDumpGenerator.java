package org.tanzu.thstudio.backup;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.sql.Types;
import java.util.List;
import java.util.zip.GZIPOutputStream;

@Component
public class SqlDumpGenerator {

    private static final List<String> TABLE_ORDER = List.of(
            "site_config",
            "portfolio_set",
            "portfolio_item",
            "webcomic_series",
            "webcomic_issue",
            "webcomic_page"
    );

    public byte[] dump(Connection connection) throws Exception {
        connection.setAutoCommit(false);
        connection.setReadOnly(true);

        var baos = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(baos);
             var writer = new OutputStreamWriter(gzip, StandardCharsets.UTF_8)) {

            writer.write("SET session_replication_role = replica;\n\n");

            for (String table : TABLE_ORDER) {
                dumpTable(connection, writer, table);
            }

            writer.write("SET session_replication_role = DEFAULT;\n");
        }
        return baos.toByteArray();
    }

    private void dumpTable(Connection connection, OutputStreamWriter writer, String table) throws Exception {
        try (var stmt = connection.createStatement();
             var rs = stmt.executeQuery("SELECT * FROM " + table + " ORDER BY id")) {

            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();

            var cols = new StringBuilder();
            for (int i = 1; i <= colCount; i++) {
                if (i > 1) cols.append(", ");
                cols.append(meta.getColumnName(i));
            }

            writer.write("DELETE FROM " + table + ";\n");

            while (rs.next()) {
                var values = new StringBuilder();
                for (int i = 1; i <= colCount; i++) {
                    if (i > 1) values.append(", ");
                    values.append(formatValue(rs, i, meta.getColumnType(i)));
                }
                writer.write("INSERT INTO " + table + " (" + cols + ") VALUES (" + values + ");\n");
            }

            writer.write("\n");
        }
    }

    private String formatValue(java.sql.ResultSet rs, int col, int sqlType) throws Exception {
        return switch (sqlType) {
            case Types.VARCHAR, Types.CHAR, Types.NVARCHAR, Types.LONGNVARCHAR, Types.CLOB -> {
                String val = rs.getString(col);
                yield rs.wasNull() ? "NULL" : "'" + val.replace("'", "''") + "'";
            }
            case Types.BIGINT, Types.INTEGER, Types.SMALLINT, Types.TINYINT -> {
                long val = rs.getLong(col);
                yield rs.wasNull() ? "NULL" : String.valueOf(val);
            }
            case Types.BOOLEAN, Types.BIT -> {
                boolean val = rs.getBoolean(col);
                yield rs.wasNull() ? "NULL" : (val ? "TRUE" : "FALSE");
            }
            case Types.DATE -> {
                var val = rs.getDate(col);
                yield rs.wasNull() ? "NULL" : "DATE '" + val + "'";
            }
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> {
                var val = rs.getTimestamp(col);
                yield rs.wasNull() ? "NULL" : "TIMESTAMP '" + val.toLocalDateTime()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "'";
            }
            default -> {
                String val = rs.getString(col);
                yield rs.wasNull() ? "NULL" : "'" + val.replace("'", "''") + "'";
            }
        };
    }
}

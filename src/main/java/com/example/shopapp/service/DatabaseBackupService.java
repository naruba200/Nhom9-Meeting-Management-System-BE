package com.example.shopapp.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DatabaseBackupService {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final JdbcTemplate jdbcTemplate;

    public byte[] createMySqlDump() {
        try {
            String currentDatabase = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);

            List<String> tableNames = jdbcTemplate.queryForList(
                    "SELECT table_name FROM information_schema.tables " +
                    "WHERE table_schema = DATABASE() ORDER BY table_name",
                    String.class);

            StringBuilder dump = new StringBuilder();
            dump.append("-- Meeting Management System SQL Dump\n");
            dump.append("-- Generated at: ").append(LocalDateTime.now().format(TIMESTAMP_FORMATTER)).append("\n");
            dump.append("-- Database: ").append(currentDatabase).append("\n\n");
            dump.append("SET NAMES utf8mb4;\n");
            dump.append("SET SQL_MODE = \"NO_AUTO_VALUE_ON_ZERO\";\n");
            dump.append("SET UNIQUE_CHECKS = 0;\n");
            dump.append("SET FOREIGN_KEY_CHECKS = 0;\n\n");
            dump.append("CREATE DATABASE IF NOT EXISTS `")
                .append(currentDatabase.replace("`", "``"))
                .append("` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\n");
            dump.append("USE `")
                .append(currentDatabase.replace("`", "``"))
                .append("`;\n\n");

            for (String tableName : tableNames) {
                String escapedTableName = tableName.replace("`", "``");
            String createTableSql = jdbcTemplate.queryForObject(
                "SHOW CREATE TABLE `" + escapedTableName + "`",
                (rs, rowNum) -> rs.getString("Create Table")
            );

            dump.append("-- ----------------------------\n");
            dump.append("-- Table structure for `").append(escapedTableName).append("`\n");
            dump.append("-- ----------------------------\n");
            dump.append("DROP TABLE IF EXISTS `").append(escapedTableName).append("`;\n");
            dump.append(createTableSql).append(";\n\n");

            List<String> columns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = ? ORDER BY ordinal_position",
                String.class,
                tableName
            );

            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM `" + escapedTableName + "`");
            if (!rows.isEmpty()) {
                String columnList = columns.stream()
                    .map(column -> "`" + column.replace("`", "``") + "`")
                    .collect(Collectors.joining(", "));

                dump.append("-- ----------------------------\n");
                dump.append("-- Records of `").append(escapedTableName).append("`\n");
                dump.append("-- ----------------------------\n");

                for (Map<String, Object> row : rows) {
                String valueList = columns.stream()
                    .map(column -> toSqlValue(row.get(column)))
                    .collect(Collectors.joining(", "));
                dump.append("INSERT INTO `")
                    .append(escapedTableName)
                    .append("` (")
                    .append(columnList)
                    .append(") VALUES (")
                    .append(valueList)
                    .append(");\n");
                }
                dump.append("\n");
                }
            }

            dump.append("SET UNIQUE_CHECKS = 1;\n");
            dump.append("SET FOREIGN_KEY_CHECKS = 1;\n");
            return dump.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new RuntimeException("Không thể tạo bản sao cơ sở dữ liệu", ex);
        }
    }

    private String toSqlValue(Object value) {
        if (value == null) {
            return "NULL";
        }

        if (value instanceof Number || value instanceof BigDecimal) {
            return value.toString();
        }

        if (value instanceof Boolean bool) {
            return bool ? "1" : "0";
        }

        if (value instanceof Timestamp ts) {
            return quote(ts.toLocalDateTime().toString().replace('T', ' '));
        }

        if (value instanceof LocalDateTime dateTime) {
            return quote(dateTime.toString().replace('T', ' '));
        }

        if (value instanceof LocalDate localDate) {
            return quote(localDate.toString());
        }

        if (value instanceof LocalTime localTime) {
            return quote(localTime.toString());
        }

        if (value instanceof byte[] bytes) {
            StringBuilder hex = new StringBuilder("0x");
            for (byte b : bytes) {
                hex.append(String.format("%02X", b));
            }
            return hex.toString();
        }

        return quote(value.toString());
    }

    private String quote(String value) {
        String escaped = value
                .replace("\\", "\\\\")
                .replace("'", "''")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return "'" + escaped + "'";
    }
}

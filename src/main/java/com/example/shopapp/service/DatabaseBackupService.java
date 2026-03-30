package com.example.shopapp.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class DatabaseBackupService {

    private final EntityManager entityManager;

    @Value("${spring.datasource.url:jdbc:mysql://127.0.0.1:3306/meetingmanage}")
    private String datasourceUrl;

    @Value("${spring.datasource.username:root}")
    private String datasourceUsername;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    @Value("${cloudinary.cloud-name}")
    private String cloudinaryCloudName;

    @Value("${cloudinary.api-key}")
    private String cloudinaryApiKey;

    @Value("${cloudinary.api-secret}")
    private String cloudinaryApiSecret;

    @Value("${cloudinary.backup-folder:meeting-management/database-backups}")
    private String backupFolder;

    @Value("${backup.cloudinary.auto-enabled:true}")
    private boolean autoBackupEnabled;

    // Track the last backup timestamp
    private LocalDateTime lastBackupTime;

    /**
     * Creates a database backup and returns it as a downloadable file
     */
    public Map<String, Object> createDatabaseBackup() throws IOException {
        try {
            log.info("Starting database backup creation...");

            // Generate SQL dump
            byte[] backupData = generateSqlDump();
            
            if (backupData == null || backupData.length == 0) {
                throw new IOException("Failed to generate database backup");
            }
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String fileName = String.format("meeting-manage-backup-%s.sql", timestamp);
            
            log.info("Database backup created successfully. File size: {} bytes", backupData.length);
            
            return Map.of(
                "success", true,
                "fileName", fileName,
                "data", backupData,
                "message", "Backup tạo thành công"
            );
        } catch (Exception e) {
            log.error("Error creating database backup", e);
            throw new IOException("Không thể tạo backup cơ sở dữ liệu: " + e.getMessage(), e);
        }
    }

    /**
     * Generates SQL dump by querying the database via JDBC
     */
    private byte[] generateSqlDump() throws SQLException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8);
        
        try {
            // Get database connection from EntityManager
            Session session = entityManager.unwrap(Session.class);
            
            session.doWork(connection -> {
                try {
                    String databaseName = extractDatabaseName(datasourceUrl);
                    
                    // Write header
                    writer.write("-- Meeting Management System Database Backup\n");
                    writer.write("-- Generated: " + LocalDateTime.now() + "\n");
                    writer.write("-- Database: " + databaseName + "\n\n");
                    writer.write("SET FOREIGN_KEY_CHECKS=0;\n\n");
                    
                    // Get all tables
                    DatabaseMetaData metaData = connection.getMetaData();
                    ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE"});
                    
                    List<String> tableNames = new ArrayList<>();
                    while (tables.next()) {
                        String tableName = tables.getString("TABLE_NAME");
                        tableNames.add(tableName);
                    }
                    tables.close();
                    
                    // Backup each table
                    for (String tableName : tableNames) {
                        backupTable(connection, tableName, writer);
                    }
                    
                    writer.write("\nSET FOREIGN_KEY_CHECKS=1;\n");
                    writer.flush();
                } catch (Exception e) {
                    log.error("Error during SQL dump generation", e);
                    throw new RuntimeException("Failed to generate SQL dump", e);
                }
            });
            
            writer.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generating SQL dump", e);
            throw new IOException("Failed to generate SQL dump: " + e.getMessage(), e);
        }
    }

    /**
     * Backs up a single table's schema and data
     */
    private void backupTable(Connection connection, String tableName, OutputStreamWriter writer) throws SQLException, IOException {
        try {
            // Get CREATE TABLE statement
            String createStatement = getCreateTableStatement(connection, tableName);
            writer.write("\n-- Table: " + tableName + "\n");
            writer.write("DROP TABLE IF EXISTS `" + tableName + "`;\n");
            writer.write(createStatement + ";\n\n");
            
            // Get table data
            String query = "SELECT * FROM " + tableName;
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                
                if (rs.next()) {
                    rs.beforeFirst(); // Reset to beginning
                    ResultSetMetaData rsMetaData = rs.getMetaData();
                    int columnCount = rsMetaData.getColumnCount();
                    
                    writer.write("-- Data for table `" + tableName + "`:\n");
                    
                    while (rs.next()) {
                        writer.write("INSERT INTO `" + tableName + "` VALUES (");
                        
                        for (int i = 1; i <= columnCount; i++) {
                            Object value = rs.getObject(i);
                            
                            if (value == null) {
                                writer.write("NULL");
                            } else if (value instanceof String) {
                                writer.write("'" + escapeString((String) value) + "'");
                            } else if (value instanceof java.sql.Timestamp) {
                                writer.write("'" + value.toString() + "'");
                            } else if (value instanceof java.sql.Date) {
                                writer.write("'" + value.toString() + "'");
                            } else if (value instanceof byte[]) {
                                writer.write("0x" + bytesToHex((byte[]) value));
                            } else {
                                writer.write(value.toString());
                            }
                            
                            if (i < columnCount) {
                                writer.write(", ");
                            }
                        }
                        
                        writer.write(");\n");
                    }
                    
                    writer.write("\n");
                }
            }
        } catch (Exception e) {
            log.warn("Could not backup table {}: {}", tableName, e.getMessage());
            // Continue with next table instead of failing
        }
    }

    /**
     * Gets the CREATE TABLE statement
     */
    private String getCreateTableStatement(Connection connection, String tableName) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW CREATE TABLE " + tableName)) {
            
            if (rs.next()) {
                return rs.getString(2); // The CREATE TABLE statement is in the 2nd column
            }
        }
        
        return "CREATE TABLE `" + tableName + "` (id INT PRIMARY KEY)"; // Fallback
    }

    /**
     * Escapes special characters in SQL strings
     */
    private String escapeString(String value) {
        return value.replace("\\", "\\\\")
                   .replace("'", "\\'")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\0", "\\0");
    }

    /**
     * Converts bytes to hexadecimal string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Creates a database backup and uploads it to Cloudinary
     */
    public Map<String, Object> createCloudinaryBackup() {
        try {
            log.info("Starting Cloudinary backup creation and upload...");

            // Create database backup
            Map<String, Object> backupResult = createDatabaseBackup();
            byte[] backupData = (byte[]) backupResult.get("data");
            String fileName = (String) backupResult.get("fileName");
            
            // Upload to Cloudinary
            String cloudinaryUrl = uploadToCloudinary(backupData, fileName);
            
            log.info("Backup uploaded to Cloudinary: {}", cloudinaryUrl);
            
            // Update last backup time
            this.lastBackupTime = LocalDateTime.now();
            
            return Map.of(
                "success", true,
                "message", "Backup đã được tạo và tải lên Cloudinary thành công",
                "url", cloudinaryUrl,
                "fileName", fileName
            );
        } catch (Exception e) {
            log.error("Error creating Cloudinary backup", e);
            throw new RuntimeException("Không thể tạo backup lên Cloudinary: " + e.getMessage(), e);
        }
    }

    /**
     * Uploads backup file to Cloudinary
     */
    private String uploadToCloudinary(byte[] backupData, String fileName) throws IOException {
        try {
            log.debug("Uploading backup to Cloudinary: {}", fileName);

            Cloudinary cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudinaryCloudName,
                "api_key", cloudinaryApiKey,
                "api_secret", cloudinaryApiSecret
            ));

            // Create a temporary file from byte array
            File tempFile = File.createTempFile("backup-", ".sql");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(backupData);
            }

            try {
                // Upload to Cloudinary
                Map<String, Object> uploadResult = cloudinary.uploader().upload(
                    tempFile,
                    ObjectUtils.asMap(
                        "folder", backupFolder,
                        "resource_type", "raw",
                        "public_id", fileName.replace(".sql", "")
                    )
                );

                String url = (String) uploadResult.get("secure_url");
                log.info("Backup uploaded successfully to: {}", url);
                return url;
            } finally {
                // Clean up temp file
                if (tempFile.exists()) {
                    tempFile.delete();
                }
            }
        } catch (Exception e) {
            log.error("Error uploading to Cloudinary", e);
            throw new IOException("Không thể tải backup lên Cloudinary: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts database name from JDBC URL
     */
    private String extractDatabaseName(String url) {
        try {
            // Format: jdbc:mysql://host:port/dbname?params
            String[] parts = url.split("/");
            String dbNameWithParams = parts[parts.length - 1];
            return dbNameWithParams.split("\\?")[0];
        } catch (Exception e) {
            log.warn("Could not extract database name, using default. Error: {}", e.getMessage());
            return "meetingmanage";
        }
    }

    /**
     * Creates a Resource object for file download response
     */
    public Resource createDownloadResource(byte[] data, String fileName) {
        return new ByteArrayResource(data) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
    }

    /**
     * Automatically backs up the database to Cloudinary every 12 hours (at 00:00 and 12:00)
     * Cron expression: 0 0 0,12 * * * (minute hour day month weekday)
     */
    @Scheduled(cron = "0 0 0,12 * * *", zone = "Asia/Ho_Chi_Minh")
    public void automaticCloudinaryBackup() {
        try {
            if (!autoBackupEnabled) {
                log.debug("Automatic Cloudinary backup is disabled");
                return;
            }

            log.info("========== STARTING AUTOMATIC CLOUDINARY BACKUP ==========");
            LocalDateTime startTime = LocalDateTime.now();
            
            // Create and upload backup to Cloudinary
            Map<String, Object> result = createCloudinaryBackup();
            
            LocalDateTime endTime = LocalDateTime.now();
            String timestamp = endTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            
            log.info("✅ Automatic Cloudinary backup completed successfully at {}", timestamp);
            log.info("   Backup URL: {}", result.get("url"));
            log.info("========== BACKUP PROCESS FINISHED ==========");
            
        } catch (Exception e) {
            log.error("❌ Automatic Cloudinary backup failed at {}", 
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), e);
        }
    }

    /**
     * Gets the timestamp of the last backup
     */
    public LocalDateTime getLastBackupTime() {
        return lastBackupTime;
    }

    /**
     * Gets the last backup time as a formatted string for display
     */
    public String getLastBackupTimeFormatted() {
        if (lastBackupTime == null) {
            return "Chưa có bản sao lưu";
        }
        return lastBackupTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}

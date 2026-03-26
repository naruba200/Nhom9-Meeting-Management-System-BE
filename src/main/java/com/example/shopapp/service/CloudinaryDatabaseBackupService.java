package com.example.shopapp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryDatabaseBackupService {

    private static final DateTimeFormatter FILE_TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final DatabaseBackupService databaseBackupService;

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    @Value("${cloudinary.backup-folder:${cloudinary.folder-prefix:meeting-management}/database-backups}")
    private String backupFolder;

    @Value("${backup.cloudinary.auto-enabled:true}")
    private boolean autoBackupEnabled;

    public void createAndUploadBackup() {
        if (!autoBackupEnabled) {
            log.info("[CloudinaryBackup] Auto backup is disabled by configuration");
            return;
        }

        validateConfiguration();

        byte[] dumpBytes = databaseBackupService.createMySqlDump();
        String timestampString = LocalDateTime.now().format(FILE_TS_FORMAT);
        String fileName = "meeting-manage-backup-" + timestampString + ".sql";
        String publicId = "db-backup-" + timestampString;
        long timestamp = Instant.now().getEpochSecond();

        String normalizedFolder = backupFolder.trim();
        String signature = signUpload(normalizedFolder, publicId, timestamp);
        String uploadUrl = "https://api.cloudinary.com/v1_1/" + cloudName.trim() + "/raw/upload";

        ByteArrayResource fileResource = new ByteArrayResource(dumpBytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", fileResource);
        body.add("api_key", apiKey.trim());
        body.add("timestamp", String.valueOf(timestamp));
        body.add("signature", signature);
        body.add("folder", normalizedFolder);
        body.add("public_id", publicId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.postForEntity(uploadUrl, new HttpEntity<>(body, headers), String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Cloudinary upload failed with status: " + response.getStatusCode());
        }

        log.info("[CloudinaryBackup] Backup uploaded successfully to folder '{}' with public_id '{}'", normalizedFolder, publicId);
    }

    private void validateConfiguration() {
        if (cloudName == null || apiKey == null || apiSecret == null || backupFolder == null) {
            throw new IllegalStateException("Cloudinary backup configuration is incomplete");
        }

        if (cloudName.isBlank() || apiKey.isBlank() || apiSecret.isBlank() || backupFolder.isBlank()) {
            throw new IllegalStateException("Cloudinary backup configuration is incomplete");
        }
    }

    private String signUpload(String folder, String publicId, long timestamp) {
        String toSign = "folder=" + folder + "&public_id=" + publicId + "&timestamp=" + timestamp;
        return sha1Hex(toSign + apiSecret.trim());
    }

    private String sha1Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot create SHA-1 signature", exception);
        }
    }
}

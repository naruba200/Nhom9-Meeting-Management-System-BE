package com.example.shopapp.service;

import com.example.shopapp.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

@Service
public class CloudinaryUploadService {

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    @Value("${cloudinary.folder-prefix:meeting-management}")
    private String folderPrefix;

    public SignedUploadPayload createSignedUploadPayload(Long meetingId, String originalFileName) {
        validateConfiguration();

        long timestamp = Instant.now().getEpochSecond();
        String folder = String.format("%s/meetings/%d", folderPrefix.trim(), meetingId);
        String publicId = String.format("%s-%d", sanitizeBaseName(originalFileName), timestamp);
        String normalizedSecret = apiSecret.trim();

        String toSign = String.format("folder=%s&public_id=%s&timestamp=%d", folder, publicId, timestamp);
        String signature = sha1Hex(toSign + normalizedSecret);

        return new SignedUploadPayload(cloudName.trim(), apiKey.trim(), timestamp, signature, folder, publicId, "auto");
    }

    public void validateSecureUrl(String secureUrl) {
        if (secureUrl == null || secureUrl.isBlank()) {
            throw new BadRequestException("Secure URL không hợp lệ");
        }

        String expectedHostSegment = "res.cloudinary.com/" + cloudName + "/";
        if (!secureUrl.contains(expectedHostSegment)) {
            throw new BadRequestException("Secure URL không thuộc tài khoản Cloudinary đã cấu hình");
        }
    }

    private void validateConfiguration() {
        if (cloudName == null || apiKey == null || apiSecret == null) {
            throw new BadRequestException("Cloudinary chưa được cấu hình đầy đủ ở backend");
        }

        if (cloudName.trim().isBlank() || apiKey.trim().isBlank() || apiSecret.trim().isBlank()) {
            throw new BadRequestException("Cloudinary chưa được cấu hình đầy đủ ở backend");
        }
    }

    private String sanitizeBaseName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "file";
        }

        String base = fileName;
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            base = fileName.substring(0, dotIndex);
        }

        String normalized = base.toLowerCase().replaceAll("[^a-z0-9-]+", "-").replaceAll("-+", "-");
        normalized = normalized.replaceAll("^-|-$", "");
        return normalized.isBlank() ? "file" : normalized;
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
            throw new IllegalStateException("Không thể tạo SHA-1 signature", exception);
        }
    }

    public record SignedUploadPayload(
            String cloudName,
            String apiKey,
            Long timestamp,
            String signature,
            String folder,
            String publicId,
            String resourceType
    ) {
    }
}

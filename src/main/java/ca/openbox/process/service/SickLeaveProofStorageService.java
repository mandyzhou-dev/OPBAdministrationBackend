package ca.openbox.process.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class SickLeaveProofStorageService {
    private static final DateTimeFormatter STORAGE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final long MAX_PROOF_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg", "webp", "heic", "heif");
    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/webp",
            "image/heic",
            "image/heif"
    );

    private final Path sickProofDirectory;
    Clock clock = Clock.systemDefaultZone();

    SickLeaveProofStorageService(@Value("${uploads.sick-proof-dir}") String sickProofDir) {
        if (sickProofDir == null || sickProofDir.isBlank()) {
            throw new IllegalArgumentException("uploads.sick-proof-dir must be configured");
        }
        this.sickProofDirectory = Path.of(sickProofDir).toAbsolutePath().normalize();
    }

    public StoredSickLeaveProof store(Integer applicationId, MultipartFile proof) {
        validateApplicationId(applicationId);
        String extension = validateProof(proof);
        Path applicationDirectory = sickProofDirectory.resolve(applicationId.toString()).normalize();
        Path target = applicationDirectory.resolve(generateStoredFilename(extension)).normalize();
        if (!target.startsWith(sickProofDirectory)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid proof storage path");
        }

        try {
            Files.createDirectories(applicationDirectory);
            Files.copy(proof.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store sick leave proof", e);
        }

        String storedFilename = sickProofDirectory.relativize(target).toString();
        return new StoredSickLeaveProof(
                storedFilename,
                target.toString(),
                proof.getOriginalFilename(),
                resolveStoredContentType(proof, extension),
                proof.getSize()
        );
    }

    private void validateApplicationId(Integer applicationId) {
        if (applicationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Application id is required");
        }
    }

    private String validateProof(MultipartFile proof) {
        if (proof == null || proof.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proof file is required");
        }
        if (proof.getSize() > MAX_PROOF_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proof file must be 10 MB or less");
        }

        String extension = extractExtension(proof.getOriginalFilename());
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proof file must be a PDF or image file");
        }

        if ("pdf".equals(extension)) {
            validatePdfSignature(proof);
            return extension;
        }

        if (!IMAGE_CONTENT_TYPES.contains(normalizeContentType(proof.getContentType()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proof file must be a PDF or image file");
        }
        return extension;
    }

    private void validatePdfSignature(MultipartFile proof) {
        byte[] header = new byte[5];
        try (InputStream inputStream = proof.getInputStream()) {
            int read = inputStream.read(header);
            if (read < header.length
                    || header[0] != '%'
                    || header[1] != 'P'
                    || header[2] != 'D'
                    || header[3] != 'F'
                    || header[4] != '-') {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proof file content does not match its file extension.");
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Failed to read proof file", e);
        }
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "";
        }
        int extensionStart = originalFilename.lastIndexOf('.');
        if (extensionStart < 0 || extensionStart == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(extensionStart + 1).toLowerCase(Locale.US);
    }

    private String resolveStoredContentType(MultipartFile proof, String extension) {
        if ("pdf".equals(extension)) {
            return "application/pdf";
        }
        return normalizeContentType(proof.getContentType());
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "";
        }
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.US);
    }

    private String generateStoredFilename(String extension) {
        String timestamp = LocalDateTime.now(clock).format(STORAGE_TIME_FORMATTER);
        return timestamp + "_" + UUID.randomUUID() + "." + extension;
    }
}

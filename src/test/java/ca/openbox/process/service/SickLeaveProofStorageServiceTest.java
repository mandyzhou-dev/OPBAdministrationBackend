package ca.openbox.process.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SickLeaveProofStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storesSupportedProofInsideApplicationDirectory() throws Exception {
        SickLeaveProofStorageService service = new SickLeaveProofStorageService(tempDir.toString());
        MockMultipartFile proof = new MockMultipartFile(
                "proof",
                "Doctor Note.PDF",
                "application/octet-stream",
                "%PDF-1.6\npdf content".getBytes()
        );

        StoredSickLeaveProof storedProof = service.store(42, proof);

        Path storedPath = tempDir.resolve(storedProof.getStoredFilename()).normalize();
        assertTrue(storedPath.startsWith(tempDir));
        assertTrue(Files.exists(storedPath));
        assertTrue(storedProof.getStoredFilename().startsWith("42/"));
        assertEquals(storedPath.toString(), storedProof.getStoredPath());
        assertEquals("Doctor Note.PDF", storedProof.getOriginalFilename());
        assertEquals("application/pdf", storedProof.getContentType());
        assertEquals(proof.getSize(), storedProof.getFileSizeBytes());
    }

    @Test
    void storesSupportedImageUsingClientContentType() throws Exception {
        SickLeaveProofStorageService service = new SickLeaveProofStorageService(tempDir.toString());
        MockMultipartFile proof = new MockMultipartFile(
                "proof",
                "note.png",
                "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00}
        );

        StoredSickLeaveProof storedProof = service.store(42, proof);

        assertTrue(storedProof.getStoredFilename().endsWith(".png"));
        assertEquals("image/png", storedProof.getContentType());
    }

    @Test
    void storesPdfWhenBrowserSendsGenericBinaryContentType() throws Exception {
        SickLeaveProofStorageService service = new SickLeaveProofStorageService(tempDir.toString());
        MockMultipartFile proof = new MockMultipartFile(
                "proof",
                "Doctor Note.pdf",
                "application/octet-stream",
                "%PDF-1.7\npdf content".getBytes()
        );

        StoredSickLeaveProof storedProof = service.store(42, proof);

        assertTrue(storedProof.getStoredFilename().startsWith("42/"));
        assertTrue(Files.exists(tempDir.resolve(storedProof.getStoredFilename()).normalize()));
    }

    @Test
    void storesPdfWhenContentTypeIsMissingButSignatureIsValid() throws Exception {
        SickLeaveProofStorageService service = new SickLeaveProofStorageService(tempDir.toString());
        MockMultipartFile proof = new MockMultipartFile(
                "proof",
                "02-311004.pdf",
                null,
                "%PDF-1.6\npdf content".getBytes()
        );

        StoredSickLeaveProof storedProof = service.store(42, proof);

        assertTrue(storedProof.getStoredFilename().startsWith("42/"));
    }

    @Test
    void storesPdfWhenContentTypeHasParametersAndSignatureIsValid() throws Exception {
        SickLeaveProofStorageService service = new SickLeaveProofStorageService(tempDir.toString());
        MockMultipartFile proof = new MockMultipartFile(
                "proof",
                "02-311004.pdf",
                "application/pdf; charset=binary",
                "%PDF-1.6\npdf content".getBytes()
        );

        StoredSickLeaveProof storedProof = service.store(42, proof);

        assertTrue(storedProof.getStoredFilename().startsWith("42/"));
    }

    @Test
    void rejectsPdfExtensionWithGenericBinaryContentTypeWhenContentIsNotPdf() {
        SickLeaveProofStorageService service = new SickLeaveProofStorageService(tempDir.toString());
        MockMultipartFile proof = new MockMultipartFile(
                "proof",
                "Doctor Note.pdf",
                "application/octet-stream",
                "not a pdf".getBytes()
        );

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.store(42, proof));

        assertEquals("Proof file content does not match its file extension.", exception.getReason());
    }

    @Test
    void rejectsPdfExtensionWithPdfContentTypeWhenContentIsNotPdf() {
        SickLeaveProofStorageService service = new SickLeaveProofStorageService(tempDir.toString());
        MockMultipartFile proof = new MockMultipartFile(
                "proof",
                "Doctor Note.pdf",
                "application/pdf",
                "not a pdf".getBytes()
        );

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.store(42, proof));

        assertEquals("Proof file content does not match its file extension.", exception.getReason());
    }

    @Test
    void rejectsUnsupportedProofType() {
        SickLeaveProofStorageService service = new SickLeaveProofStorageService(tempDir.toString());
        MockMultipartFile proof = new MockMultipartFile(
                "proof",
                "note.exe",
                "application/octet-stream",
                "bad".getBytes()
        );

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.store(42, proof));

        assertEquals("Proof file must be a PDF or image file", exception.getReason());
    }
}

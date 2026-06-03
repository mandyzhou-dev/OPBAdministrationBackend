package ca.openbox.process.service;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StoredSickLeaveProof {
    private String storedFilename;
    private String storedPath;
    private String originalFilename;
    private String contentType;
    private Long fileSizeBytes;
}

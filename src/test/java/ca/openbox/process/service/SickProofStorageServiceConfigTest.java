package ca.openbox.process.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SickProofStorageServiceConfigTest {

    @Test
    void writesSmallTxtFileToConfiguredSickProofDirectory() throws Exception {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TestConfig.class)
                .web(WebApplicationType.NONE)
                .properties(Map.of("spring.config.location", "file:/etc/openbox/config.yml"))
                .run()) {
            SickProofTestFileWriter writer = context.getBean(SickProofTestFileWriter.class);
            Path configuredDirectory = writer.getSickProofDirectory();
            assertFalse(configuredDirectory.toString().isBlank());
            writer.ensureSickProofDirectoryExists();
            assertTrue(Files.isWritable(configuredDirectory),
                    () -> "uploads.sick-proof-dir is not writable: " + configuredDirectory);

            String content = "sick proof config test";
            String filename = "sick-proof-config-test-" + UUID.randomUUID() + ".txt";
            Path writtenFile = null;
            try {
                writtenFile = writer.writeTextProof(filename, content);

                assertTrue(writtenFile.startsWith(configuredDirectory));
                assertTrue(Files.exists(writtenFile));
                assertEquals(content, Files.readString(writtenFile, StandardCharsets.UTF_8));
            } finally {
                if (writtenFile != null) {
                    Files.deleteIfExists(writtenFile);
                }
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        SickProofTestFileWriter sickProofTestFileWriter(@Value("${uploads.sick-proof-dir}") String sickProofDir) {
            return new SickProofTestFileWriter(sickProofDir);
        }
    }
}

class SickProofTestFileWriter {

    private final Path sickProofDirectory;

    SickProofTestFileWriter(String sickProofDir) {
        if (sickProofDir == null || sickProofDir.isBlank()) {
            throw new IllegalArgumentException("uploads.sick-proof-dir must be configured");
        }
        if (sickProofDir.contains("{{") || sickProofDir.contains("}}")) {
            throw new IllegalArgumentException("uploads.sick-proof-dir still contains an unresolved template value");
        }
        this.sickProofDirectory = Path.of(sickProofDir).toAbsolutePath().normalize();
    }

    Path getSickProofDirectory() {
        return sickProofDirectory;
    }

    void ensureSickProofDirectoryExists() throws IOException {
        Files.createDirectories(sickProofDirectory);
    }

    Path writeTextProof(String filename, String content) throws IOException {
        ensureSickProofDirectoryExists();
        Path target = sickProofDirectory.resolve(filename).normalize();
        if (!target.startsWith(sickProofDirectory)) {
            throw new IllegalArgumentException("filename must stay inside uploads.sick-proof-dir");
        }
        return Files.writeString(
                target,
                content,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
        );
    }
}

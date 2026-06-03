package ca.openbox.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExternalMultipartConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void externalConfigLocationBindsMultipartLimits() throws Exception {
        Path config = tempDir.resolve("config.yml");
        Files.writeString(config, """
                spring:
                  servlet:
                    multipart:
                      max-file-size: 50MB
                      max-request-size: 200MB
                """);

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TestConfig.class)
                .web(WebApplicationType.NONE)
                .properties("spring.config.location=" + config.toUri())
                .run()) {
            MultipartProperties multipartProperties = context.getBean(MultipartProperties.class);

            assertEquals(DataSize.ofMegabytes(50), multipartProperties.getMaxFileSize());
            assertEquals(DataSize.ofMegabytes(200), multipartProperties.getMaxRequestSize());
        }
    }

    @Configuration
    @EnableConfigurationProperties(MultipartProperties.class)
    static class TestConfig {
    }
}

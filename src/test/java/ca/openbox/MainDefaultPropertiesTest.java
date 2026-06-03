package ca.openbox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainDefaultPropertiesTest {

    @Test
    void defaultPropertiesIncludeExternalConfigLocationAndMultipartLimits() {
        assertEquals("file:/etc/openbox/config.yml", Main.defaultProperties().get("spring.config.location"));
        assertEquals("50MB", Main.defaultProperties().get("spring.servlet.multipart.max-file-size"));
        assertEquals("200MB", Main.defaultProperties().get("spring.servlet.multipart.max-request-size"));
    }
}

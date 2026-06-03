package ca.openbox;

import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
@EnableEncryptableProperties
@EnableScheduling
public class Main {
    public static void main(String[] args) {
        SpringApplication springApplication = new SpringApplication(Main.class);
        springApplication.setDefaultProperties(defaultProperties());
        springApplication.run(args);
    }

    static Map<String, Object> defaultProperties() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.config.location", "file:/etc/openbox/config.yml");
        properties.put("spring.servlet.multipart.max-file-size", "50MB");
        properties.put("spring.servlet.multipart.max-request-size", "200MB");
        return properties;
    }
}

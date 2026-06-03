package ca.openbox.process.service.components;

import ca.openbox.infrastructure.email.service.WebhookEmailService;
import ca.openbox.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailNotificationConsumerSpringWiringTest {

    @Test
    void runtimeConstructorIsExplicitlyAutowiredWhenTestConstructorAlsoExists() throws Exception {
        Constructor<EmailNotificationConsumer> runtimeConstructor =
                EmailNotificationConsumer.class.getConstructor(WebhookEmailService.class, UserRepository.class);

        assertTrue(runtimeConstructor.isAnnotationPresent(Autowired.class));
    }
}

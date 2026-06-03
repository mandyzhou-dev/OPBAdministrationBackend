package ca.openbox.infrastructure.email.service;

import ca.openbox.infrastructure.email.dto.WebhookEmailDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
@Service
public class WebhookEmailService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    @Value("${mail.url}")
    private String url;
    @Value("${mail.webtoken}")
    private String webtoken;
    public void sendEmail(String recipient, String subject, String content) throws IOException, InterruptedException {
        String endpoint=String.format(url);

        String emailJson = buildEmailJson(recipient, subject, content, webtoken);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Bearer " + webtoken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(emailJson))
                .build();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("Response code: " + response.statusCode());
        System.out.println("Response body: " + response.body());


    }

    static String buildEmailJson(String recipient, String subject, String content, String token) throws IOException {
        WebhookEmailDTO emailDTO = new WebhookEmailDTO();
        emailDTO.setRecipient(recipient);
        emailDTO.setTitle(subject);
        emailDTO.setContent(content);
        emailDTO.setToken(token);
        return OBJECT_MAPPER.writeValueAsString(emailDTO);
    }
}

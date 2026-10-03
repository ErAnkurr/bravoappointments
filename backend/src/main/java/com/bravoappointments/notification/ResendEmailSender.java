package com.bravoappointments.notification;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** Sends transactional email through Resend's HTTP API. */
public class ResendEmailSender implements EmailSender {

    private final RestClient client;
    private final String from;

    public ResendEmailSender(String apiKey, String from) {
        this.client = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        this.from = from;
    }

    @Override
    public void send(String to, String subject, String html) {
        client.post()
                .uri("/emails")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("from", from, "to", List.of(to), "subject", subject, "html", html))
                .retrieve()
                .toBodilessEntity();
    }
}

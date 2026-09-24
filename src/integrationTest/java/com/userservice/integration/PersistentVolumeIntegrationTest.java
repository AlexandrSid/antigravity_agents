package com.userservice.integration;

import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PersistentVolumeIntegrationTest extends IntegrationTestSupport {

    private static final String MARKER_EMAIL = "persistent.marker@example.com";

    @Test
    void markerSurvivesComposeRestart() throws Exception {
        String phase = System.getenv().getOrDefault("INTEGRATION_PHASE", "main");
        if ("verify-persistence".equals(phase)) {
            ApiResponse response = get("/api/v1/users/by-email?email="
                    + URLEncoder.encode(MARKER_EMAIL, StandardCharsets.UTF_8));
            assertThat(response.status()).isEqualTo(200);
            assertThat(response.json().path("email").asText()).isEqualTo(MARKER_EMAIL);
            return;
        }

        ApiResponse created = post("/api/v1/users", """
                {
                  "firstName": "Persistent",
                  "lastName": "Marker",
                  "birthDate": "1990-01-01",
                  "email": "persistent.marker@example.com"
                }
                """);
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.json().path("email").asText()).isEqualTo(MARKER_EMAIL);
    }
}

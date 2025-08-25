package com.dle.dlq.container;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.time.Duration;

public abstract class KeycloakTc extends TestContainersBase {

    protected static final GenericContainer<?> KEYCLOAK =
            new GenericContainer<>("quay.io/keycloak/keycloak:24.0")
                    .withEnv("KEYCLOAK_ADMIN", "admin")
                    .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
                    .withEnv("KC_HOSTNAME_STRICT", "false")
                    .withEnv("KC_HTTP_ENABLED", "true") // extra safety for v24+
                    .withCommand("start-dev --http-port 8080 --import-realm")
                    .withExposedPorts(8080)
                    .withClasspathResourceMapping(
                            "keycloak/realm-dle.json",
                            "/opt/keycloak/data/import/realm-dle.json",
                            BindMode.READ_ONLY
                    )
                    // ✅ Wait only for the socket to accept connections
                    .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(4)));

    static {
        if (!KEYCLOAK.isRunning()) {
            KEYCLOAK.start();
        }
    }

    @DynamicPropertySource
    static void registerKeycloakProps(DynamicPropertyRegistry r) {
        String base = "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
        r.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> base + "/realms/dle");
    }

    protected static String tokenFor(String username, String password) {
        String base = "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
        var client = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        String form = "grant_type=password&client_id=dlq-backend&username=" + username + "&password=" + password;
        var req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(base + "/realms/dle/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(10))
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(form))
                .build();

        // ✅ Small retry loop to handle last‑inch readiness (import delays)
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                var resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(resp.body());
                    return json.get("access_token").asText();
                }
                if (attempts >= 10) {
                    throw new IllegalStateException("Keycloak token error: " + resp.statusCode() + " - " + resp.body());
                }
            } catch (Exception e) {
                if (attempts >= 10) throw new RuntimeException(e);
            }
            try { Thread.sleep(500L * attempts); } catch (InterruptedException ignored) {}
        }
    }
}

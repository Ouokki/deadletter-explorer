package com.dle.dlq.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(CorsConfigWebTest.TestController.class)
class CorsConfigWebTest {

    @LocalServerPort
    int port;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        // Bind to the real Netty server so requests have a proper URI scheme/host.
        this.client = WebTestClient
                .bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @RestController
    static class TestController {
        @GetMapping("/ping")
        public String ping() {
            return "pong";
        }
    }

    @Test
    void preflight_allowsConfiguredOriginAndMethod() {
        client.method(HttpMethod.OPTIONS)
                .uri("/ping")
                .header(HttpHeaders.ORIGIN, "http://example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,x-custom")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://example.com")
                .expectHeader().valueEquals("Access-Control-Allow-Credentials", "true")
                .expectHeader().value("Access-Control-Allow-Methods",
                        v -> assertThat(v).contains("GET"))
                .expectHeader().value("Access-Control-Allow-Headers",
                        v -> {
                            assertThat(v).contains("content-type");
                            assertThat(v).contains("x-custom");
                        })
                .expectHeader().values("Vary",
                        list -> assertThat(String.join(",", list))
                                .contains("Origin")
                                .contains("Access-Control-Request-Method")
                                .contains("Access-Control-Request-Headers"));
    }

    @Test
    void preflight_rejectsNotConfiguredOrigin() {
        client.method(HttpMethod.OPTIONS)
                .uri("/ping")
                .header(HttpHeaders.ORIGIN, "http://evil.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void simpleRequest_echoesAllowOrigin() {
        client.get()
                .uri("/ping")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("pong");

        client.get()
                .uri("/ping")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .exchange()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:5173");
    }
}

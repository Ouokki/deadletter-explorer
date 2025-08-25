package com.dle.dlq.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;

class CorsConfigTest {

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        // Instantiate config with a single allowed origin
        var config = new CorsConfig();
        CorsWebFilter corsFilter = config.corsWebFilter(
                "http://localhost:5173", // dle.cors.allowed-origins
                ""                       // dle.cors.allowed-origin-patterns
        );

        // Minimal test route
        RouterFunction<ServerResponse> routes = RouterFunctions.route(
                GET("/hello"),
                req -> ServerResponse.ok()
                        .header("X-Request-Id", "abc-123")
                        .header("Content-Disposition", "inline")
                        .bodyValue("ok")
        );

        this.client = WebTestClient.bindToRouterFunction(routes)
                .webFilter(corsFilter)
                .configureClient()
                .baseUrl("http://localhost")
                .build();
    }

    @Test
    @DisplayName("Preflight: allowed origin gets CORS allow headers")
    void preflight_allowsConfiguredOrigin() {
        client.options()
                .uri("/hello")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:5173")
                .expectHeader().valueEquals("Access-Control-Allow-Credentials", "true")
                .expectHeader().value("Access-Control-Allow-Methods", value -> {
                    assertThat(value).contains("GET")
                            .contains("POST")
                            .contains("PUT")
                            .contains("PATCH")
                            .contains("DELETE")
                            .contains("OPTIONS");
                })
                .expectHeader().valueEquals("Access-Control-Max-Age", "3600");
    }

    @Test
    @DisplayName("Actual request: allowed origin receives ACAO + exposed headers")
    void actualRequest_hasExposedHeaders() {
        client.get()
                .uri("/hello")
                .header("Origin", "http://localhost:5173")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Access-Control-Allow-Origin", "http://localhost:5173")
                .expectHeader().valueEquals("Access-Control-Allow-Credentials", "true")
                .expectHeader().value("Access-Control-Expose-Headers", value -> {
                    List<String> headers = Arrays.stream(value.split(","))
                            .map(String::trim)
                            .toList();
                    assertThat(headers).contains("Location", "Content-Disposition", "X-Request-Id");
                })
                .expectBody(String.class).isEqualTo("ok");
    }

    @Test
    @DisplayName("Disallowed origin: no ACAO header present (blocked by CORS)")
    void disallowedOrigin_noCorsHeaders() {
        client.get()
                .uri("/hello")
                .header("Origin", "http://evil.example.com")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist("Access-Control-Allow-Origin")
                .expectHeader().doesNotExist("Access-Control-Expose-Headers");
    }

    @Test
    @DisplayName("Preflight: disallowed origin gets 403 (security)")
    void preflight_disallowedOrigin() {
        client.options()
                .uri("/hello")
                .header("Origin", "http://evil.example.com")
                .header("Access-Control-Request-Method", "GET")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist("Access-Control-Allow-Origin")
                .expectHeader().doesNotExist("Access-Control-Allow-Methods")
                .expectHeader().doesNotExist("Access-Control-Max-Age");
    }

}

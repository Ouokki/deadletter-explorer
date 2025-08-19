package com.dle.dlq.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigUnitTest {

    private WebFilterChain noOpChain() {
        return exchange -> exchange.getResponse().setComplete();
    }

    @Test
    void preflight_isAllowed_forConfiguredOrigin() {
        var config = new CorsConfig();
        var filter = config.corsWebFilter("http://localhost:5173,http://example.com");

        var request = MockServerHttpRequest
                .method(HttpMethod.OPTIONS, URI.create("http://localhost/any"))
                .header(HttpHeaders.ORIGIN, "http://example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.HOST, "localhost")
                .build();
        var exchange = MockServerWebExchange.from(request);
        MockServerHttpResponse response = exchange.getResponse();

        filter.filter(exchange, noOpChain()).block();

        assertThat(response.getHeaders().getFirst("Access-Control-Allow-Origin"))
                .isEqualTo("http://example.com");
        assertThat(response.getHeaders().getFirst("Access-Control-Allow-Methods"))
                .isNotBlank();
        assertThat(response.getHeaders().getFirst("Vary"))
                .contains("Origin");
    }

    @Test
    void preflight_isRejected_forUnknownOrigin() {
        var config = new CorsConfig();
        var filter = config.corsWebFilter("http://localhost:5173");

        var request = MockServerHttpRequest
                .method(HttpMethod.OPTIONS, URI.create("http://localhost/any"))
                .header(HttpHeaders.ORIGIN, "http://evil.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.HOST, "localhost")
                .build();
        var exchange = MockServerWebExchange.from(request);
        MockServerHttpResponse response = exchange.getResponse();

        filter.filter(exchange, noOpChain()).block();

        assertThat(response.getHeaders().getFirst("Access-Control-Allow-Origin")).isNull();
        assertThat(response.getHeaders().getFirst("Access-Control-Allow-Methods")).isNull();
    }

    @Test
    void simpleRequest_echoesAllowedOrigin() {
        var config = new CorsConfig();
        var filter = config.corsWebFilter("http://localhost:5173");

        var request = MockServerHttpRequest
                .method(HttpMethod.GET, URI.create("http://localhost/ping"))
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.HOST, "localhost")
                .build();
        var exchange = MockServerWebExchange.from(request);
        MockServerHttpResponse response = exchange.getResponse();

        filter.filter(exchange, noOpChain()).block();

        assertThat(response.getHeaders().getFirst("Access-Control-Allow-Origin"))
                .isEqualTo("http://localhost:5173");
    }
}

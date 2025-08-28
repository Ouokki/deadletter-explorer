package com.dle.dlq.config;

import com.dle.dlq.admin.DlqAdminService;
import com.dle.dlq.kafka.service.consumer.DlqConsumerService;
import com.dle.dlq.dto.MessageDto;
import com.dle.dlq.model.ReplayRequest;
import com.dle.dlq.kafka.service.producer.DlqProducerService;
import com.dle.dlq.web.DlqController;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@WebFluxTest(controllers = { DlqController.class, SecurityConfigTest.HealthProbe.class })
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired WebTestClient web;

    @MockBean DlqAdminService admin;
    @MockBean DlqConsumerService consumer;
    @MockBean DlqProducerService producer;

    @RestController
    static class HealthProbe {
        @GetMapping("/actuator/health")
        Map<String, Object> health() { return Map.of("status", "UP"); }
    }



    @Test
    void dlq_get_allows_roles() {
        when(consumer.fetchLastN("orders-DLQ", 3)).thenReturn(List.of(
                new MessageDto("orders-DLQ", 0, 1L, 1000L, "k1", "v1", "dm1=", Map.of("h1","v")),
                new MessageDto("orders-DLQ", 0, 2L, 2000L, "k2", "v2", "dm2=", Map.of())
        ));

        // no JWT -> 401
        web.get().uri("/api/dlq/messages?topic=orders-DLQ&limit=3")
                .exchange()
                .expectStatus().isUnauthorized();

        // viewer -> 200
        web.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_viewer")))
                .get().uri("/api/dlq/messages?topic=orders-DLQ&limit=3")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBodyList(MessageDto.class).hasSize(2);

        // triager -> 200
        web.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_triager")))
                .get().uri("/api/dlq/messages?topic=orders-DLQ&limit=3")
                .exchange()
                .expectStatus().isOk();

        // replayer -> 200
        web.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_replayer")))
                .get().uri("/api/dlq/messages?topic=orders-DLQ&limit=3")
                .exchange()
                .expectStatus().isOk();

        verify(consumer, times(3)).fetchLastN("orders-DLQ", 3);
    }

    @Test
    void dlq_post_requires_stronger_role() throws Exception {
        when(producer.replay(any(ReplayRequest.class))).thenReturn(5);

        String body = """
            {
              "targetTopic": "orders",
              "items": [],
              "throttlePerSec": 50
            }
            """;

        // viewer -> 403
        web.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_viewer")))
                .post().uri("/api/dlq/replay")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isForbidden();

        // triager -> 200
        web.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_triager")))
                .post().uri("/api/dlq/replay")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Integer.class).isEqualTo(5);

        // replayer -> 200
        web.mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_replayer")))
                .post().uri("/api/dlq/replay")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk();

        verify(producer, times(2)).replay(ArgumentMatchers.any(ReplayRequest.class));
    }
}

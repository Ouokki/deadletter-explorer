package com.dle.dlq.producer;

import com.dle.dlq.dto.ReplayItem;
import com.dle.dlq.dto.ReplayRequest;
import com.dle.dlq.util.MessageMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DlqProducerServiceUnitTest {

    @Test
    void replay_sendsDecodedPayload_withTopicAndFilteredHeaders() throws Exception {
        // GIVEN
        @SuppressWarnings("unchecked")
        KafkaTemplate<byte[], byte[]> template = mock(KafkaTemplate.class);

        // KafkaTemplate#send returns a (completed) future; result not used
        when(template.send(any(Message.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        String topic = "replayed-topic";
        byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        String bodyB64 = Base64.getEncoder().encodeToString(body);

        // Request + items are plain mocks
        ReplayItem item1 = mock(ReplayItem.class);
        when(item1.valueBase64()).thenReturn(bodyB64);
        when(item1.headersBase64()).thenReturn(Map.of("content-type", "ignored"));
        ReplayRequest req = mock(ReplayRequest.class);
        when(req.targetTopic()).thenReturn(topic);
        when(req.throttlePerSec()).thenReturn(1000); // interval = 1ms
        when(req.items()).thenReturn(List.of(item1));

        // Stub MessageMapper to return the headers to be copied
        Map<String, Object> filteredHeaders = Map.of(
                "content-type", "application/json",
                "correlation-id", "abc-123"
        );

        try (MockedStatic<MessageMapper> mm = mockStatic(MessageMapper.class)) {
            mm.when(() -> MessageMapper.filterAllowed(any(), anySet()))
                    .thenReturn(filteredHeaders);

            DlqProducerService svc = new DlqProducerService("content-type,correlation-id", template);
            svc.throttlePerSec = 5000; // default, but request overrides anyway

            // WHEN
            int sent = svc.replay(req);

            // THEN
            assertThat(sent).isEqualTo(1);

            ArgumentCaptor<Message<byte[]>> captor = ArgumentCaptor.forClass(Message.class);
            verify(template, times(1)).send(captor.capture());

            Message<byte[]> msg = captor.getValue();
            assertThat(msg.getPayload()).isEqualTo(body);
            assertThat(msg.getHeaders().get(KafkaHeaders.TOPIC)).isEqualTo(topic);
            assertThat(msg.getHeaders().get("content-type")).isEqualTo("application/json");
            assertThat(msg.getHeaders().get("correlation-id")).isEqualTo("abc-123");

            // ensure we invoked the mapper with a Set of allowed headers
            mm.verify(() -> MessageMapper.filterAllowed(any(), anySet()));
        }
    }

    @Test
    void replay_usesDefaultThrottleWhenRequestNull_and_stillSendsAll() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<byte[], byte[]> template = mock(KafkaTemplate.class);
        when(template.send(any(Message.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        String topic = "t";
        byte[] body = "x".getBytes(StandardCharsets.UTF_8);
        String b64 = Base64.getEncoder().encodeToString(body);

        ReplayItem it1 = mock(ReplayItem.class);
        ReplayItem it2 = mock(ReplayItem.class);
        when(it1.valueBase64()).thenReturn(b64);
        when(it2.valueBase64()).thenReturn(b64);
        when(it1.headersBase64()).thenReturn(Map.of());
        when(it2.headersBase64()).thenReturn(Map.of());

        ReplayRequest req = mock(ReplayRequest.class);
        when(req.targetTopic()).thenReturn(topic);
        when(req.throttlePerSec()).thenReturn(null); // use service default
        when(req.items()).thenReturn(List.of(it1, it2));

        try (MockedStatic<MessageMapper> mm = mockStatic(MessageMapper.class)) {
            mm.when(() -> MessageMapper.filterAllowed(any(), anySet()))
                    .thenReturn(Map.of()); // no additional headers

            DlqProducerService svc = new DlqProducerService("content-type, correlation-id", template);
            svc.throttlePerSec = 10_000; // interval -> 1ms

            int sent = svc.replay(req);

            assertThat(sent).isEqualTo(2);
            verify(template, times(2)).send(any(Message.class));
        }
    }

    @Test
    void replay_parsesAllowList_trimAndIgnoresBlanks_and_passesSetToMapper() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<byte[], byte[]> template = mock(KafkaTemplate.class);
        when(template.send(any(Message.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        ReplayItem it = mock(ReplayItem.class);
        when(it.valueBase64()).thenReturn(Base64.getEncoder().encodeToString("v".getBytes(StandardCharsets.UTF_8)));
        when(it.headersBase64()).thenReturn(Map.of("ignored", "ignored"));
        ReplayRequest req = mock(ReplayRequest.class);
        when(req.targetTopic()).thenReturn("topic");
        when(req.throttlePerSec()).thenReturn(1000);
        when(req.items()).thenReturn(List.of(it));

        // Build service with messy allow list; we assert the Set seen by mapper
        DlqProducerService svc = new DlqProducerService("  content-type , , correlation-id , ", template);

        try (MockedStatic<MessageMapper> mm = mockStatic(MessageMapper.class)) {
            mm.when(() -> MessageMapper.filterAllowed(any(), anySet()))
                    .thenAnswer(inv -> {
                        @SuppressWarnings("unchecked")
                        Set<String> allow = inv.getArgument(1);
                        assertThat(allow).containsExactlyInAnyOrder("content-type", "correlation-id");
                        return Map.of("content-type", "application/json");
                    });

            int sent = svc.replay(req);
            assertThat(sent).isEqualTo(1);
        }

        verify(template).send(any(Message.class));
    }

    @Test
    void replay_throwsWhenTargetTopicMissing() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<byte[], byte[]> template = mock(KafkaTemplate.class);
        ReplayRequest req = mock(ReplayRequest.class);
        when(req.targetTopic()).thenReturn(null);

        DlqProducerService svc = new DlqProducerService("content-type,correlation-id", template);

        assertThatThrownBy(() -> svc.replay(req))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("targetTopic required");
        verifyNoInteractions(template);
    }
}

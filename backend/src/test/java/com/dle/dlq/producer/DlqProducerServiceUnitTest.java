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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DlqProducerServiceUnitTest {

    @Test
    void replay_sendsDecodedPayload_withTopicAndFilteredHeaders() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<byte[], byte[]> template = mock(KafkaTemplate.class);
        when(template.send(any(Message.class))).thenReturn(CompletableFuture.completedFuture(null));

        String topic = "replayed-topic";
        byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        String bodyB64 = Base64.getEncoder().encodeToString(body);

        ReplayItem item1 = mock(ReplayItem.class);
        when(item1.valueBase64()).thenReturn(bodyB64);
        when(item1.headersBase64()).thenReturn(Map.of("content-type", "ignored"));

        ReplayRequest req = mock(ReplayRequest.class);
        when(req.targetTopic()).thenReturn(topic);
        when(req.throttlePerSec()).thenReturn(1000);
        when(req.items()).thenReturn(List.of(item1));

        Map<String, Object> filteredHeaders = Map.of(
                "content-type", "application/json",
                "correlation-id", "abc-123"
        );

        try (MockedStatic<MessageMapper> mm = mockStatic(MessageMapper.class)) {
            mm.when(() -> MessageMapper.filterAllowed(any(), anySet())).thenReturn(filteredHeaders);

            DlqProducerService svc = new DlqProducerService("content-type,correlation-id", template);
            svc.throttlePerSec = 5000;

            int sent = svc.replay(req);
            assertThat(sent).isEqualTo(1);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Message<byte[]>> captor = ArgumentCaptor.forClass(Message.class);
            verify(template, times(1)).send(captor.capture());

            Message<byte[]> msg = captor.getValue();
            assertThat(msg.getPayload()).isEqualTo(body);
            assertThat(msg.getHeaders().get(KafkaHeaders.TOPIC)).isEqualTo(topic);
            assertThat(msg.getHeaders().get("content-type")).isEqualTo("application/json");
            assertThat(msg.getHeaders().get("correlation-id")).isEqualTo("abc-123");

            mm.verify(() -> MessageMapper.filterAllowed(any(), anySet()));
        }
    }

    @Test
    void replay_usesDefaultThrottleWhenRequestNull_and_stillSendsAll() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<byte[], byte[]> template = mock(KafkaTemplate.class);
        when(template.send(any(Message.class))).thenReturn(CompletableFuture.completedFuture(null));

        String topic = "t";
        String b64 = Base64.getEncoder().encodeToString("x".getBytes(StandardCharsets.UTF_8));

        ReplayItem it1 = mock(ReplayItem.class);
        ReplayItem it2 = mock(ReplayItem.class);
        when(it1.valueBase64()).thenReturn(b64);
        when(it2.valueBase64()).thenReturn(b64);
        when(it1.headersBase64()).thenReturn(Map.of());
        when(it2.headersBase64()).thenReturn(Map.of());

        ReplayRequest req = mock(ReplayRequest.class);
        when(req.targetTopic()).thenReturn(topic);
        when(req.throttlePerSec()).thenReturn(null);
        when(req.items()).thenReturn(List.of(it1, it2));

        try (MockedStatic<MessageMapper> mm = mockStatic(MessageMapper.class)) {
            mm.when(() -> MessageMapper.filterAllowed(any(), anySet())).thenReturn(Map.of());

            DlqProducerService svc = new DlqProducerService("content-type, correlation-id", template);
            svc.throttlePerSec = 10_000;

            int sent = svc.replay(req);
            assertThat(sent).isEqualTo(2);
            verify(template, times(2)).send(any(Message.class));
        }
    }

    @Test
    void replay_parsesAllowList_trimAndIgnoresBlanks_and_passesSetToMapper() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<byte[], byte[]> template = mock(KafkaTemplate.class);
        when(template.send(any(Message.class))).thenReturn(CompletableFuture.completedFuture(null));

        ReplayItem it = mock(ReplayItem.class);
        when(it.valueBase64()).thenReturn(Base64.getEncoder().encodeToString("v".getBytes(StandardCharsets.UTF_8)));
        when(it.headersBase64()).thenReturn(Map.of("ignored", "ignored"));

        ReplayRequest req = mock(ReplayRequest.class);
        when(req.targetTopic()).thenReturn("topic");
        when(req.throttlePerSec()).thenReturn(1000);
        when(req.items()).thenReturn(List.of(it));

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

    @Test
    void replay_returnsZero_whenItemsNullOrEmpty() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<byte[], byte[]> template = mock(KafkaTemplate.class);

        // items == null
        ReplayRequest reqNull = mock(ReplayRequest.class);
        when(reqNull.targetTopic()).thenReturn("t");
        when(reqNull.items()).thenReturn(null);

        DlqProducerService svc = new DlqProducerService("content-type,correlation-id", template);
        assertThat(svc.replay(reqNull)).isEqualTo(0);
        verifyNoInteractions(template);

        // items.isEmpty()
        ReplayRequest reqEmpty = mock(ReplayRequest.class);
        when(reqEmpty.targetTopic()).thenReturn("t");
        when(reqEmpty.items()).thenReturn(List.of());

        assertThat(svc.replay(reqEmpty)).isEqualTo(0);
        verifyNoMoreInteractions(template);
    }

    @Test
    void replay_skipsInvalidBase64_andContinuesWithNext() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<byte[], byte[]> template = mock(KafkaTemplate.class);
        when(template.send(any(Message.class))).thenReturn(CompletableFuture.completedFuture(null));

        ReplayItem bad = mock(ReplayItem.class);
        when(bad.valueBase64()).thenReturn("not-base64!!");
        when(bad.headersBase64()).thenReturn(Map.of());

        ReplayItem good = mock(ReplayItem.class);
        String b64 = Base64.getEncoder().encodeToString("ok".getBytes(StandardCharsets.UTF_8));
        when(good.valueBase64()).thenReturn(b64);
        when(good.headersBase64()).thenReturn(Map.of());

        ReplayRequest req = mock(ReplayRequest.class);
        when(req.targetTopic()).thenReturn("topic");
        when(req.throttlePerSec()).thenReturn(10_000);
        when(req.items()).thenReturn(List.of(bad, good));

        try (MockedStatic<MessageMapper> mm = mockStatic(MessageMapper.class)) {
            mm.when(() -> MessageMapper.filterAllowed(any(), anySet())).thenReturn(Map.of());

            DlqProducerService svc = new DlqProducerService("content-type,correlation-id", template);
            int sent = svc.replay(req);

            assertThat(sent).isEqualTo(1);             // bad skipped, good sent
            verify(template, times(1)).send(any(Message.class));
        }
    }

    @Test
    void replay_catchesSendException_andContinues() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<byte[], byte[]> template = mock(KafkaTemplate.class);

        // first send -> failing future; second -> ok
        CompletableFuture<Object> failing = new CompletableFuture<>();
        failing.completeExceptionally(new RuntimeException("send failed"));
        when(template.send(any(Message.class)))
                .thenReturn((CompletableFuture) failing)
                .thenReturn(CompletableFuture.completedFuture(null));

        String b64 = Base64.getEncoder().encodeToString("x".getBytes(StandardCharsets.UTF_8));
        ReplayItem it1 = mock(ReplayItem.class);
        ReplayItem it2 = mock(ReplayItem.class);
        when(it1.valueBase64()).thenReturn(b64);
        when(it2.valueBase64()).thenReturn(b64);
        when(it1.headersBase64()).thenReturn(Map.of());
        when(it2.headersBase64()).thenReturn(Map.of());

        ReplayRequest req = mock(ReplayRequest.class);
        when(req.targetTopic()).thenReturn("t");
        when(req.throttlePerSec()).thenReturn(10_000);
        when(req.items()).thenReturn(List.of(it1, it2));

        try (MockedStatic<MessageMapper> mm = mockStatic(MessageMapper.class)) {
            mm.when(() -> MessageMapper.filterAllowed(any(), anySet())).thenReturn(Map.of());

            DlqProducerService svc = new DlqProducerService("content-type,correlation-id", template);
            int sent = svc.replay(req);

            assertThat(sent).isEqualTo(1);
            verify(template, times(2)).send(any(Message.class));
        }
    }

    @Test
    void replay_propagatesInterrupt_afterFirstSend() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<byte[], byte[]> template = mock(KafkaTemplate.class);

        CountDownLatch firstSend = new CountDownLatch(1);
        when(template.send(any(Message.class))).thenAnswer(inv -> {
            firstSend.countDown();
            return CompletableFuture.completedFuture(null);
        });

        String b64 = Base64.getEncoder().encodeToString("x".getBytes(StandardCharsets.UTF_8));
        ReplayItem it1 = mock(ReplayItem.class);
        ReplayItem it2 = mock(ReplayItem.class);
        when(it1.valueBase64()).thenReturn(b64);
        when(it2.valueBase64()).thenReturn(b64);
        when(it1.headersBase64()).thenReturn(Map.of());
        when(it2.headersBase64()).thenReturn(Map.of());

        ReplayRequest req = mock(ReplayRequest.class);
        when(req.targetTopic()).thenReturn("t");
        when(req.throttlePerSec()).thenReturn(1); // intervalMs ~= 1000ms
        when(req.items()).thenReturn(List.of(it1, it2));

        try (MockedStatic<MessageMapper> mm = mockStatic(MessageMapper.class)) {
            mm.when(() -> MessageMapper.filterAllowed(any(), anySet())).thenReturn(Map.of());

            DlqProducerService svc = new DlqProducerService("content-type,correlation-id", template);

            // Run replay on a worker thread so we can interrupt during sleep
            var error = new AtomicReference<Throwable>();
            Thread worker = new Thread(() -> {
                try {
                    svc.replay(req);
                    error.set(new AssertionError("Expected interrupt to propagate"));
                } catch (Throwable t) {
                    error.set(t);
                }
            });

            worker.start();

            // After first send, interrupt during sleep
            assertThat(firstSend.await(2, TimeUnit.SECONDS)).isTrue();
            worker.interrupt();
            worker.join(2_000);

            // Accept either InterruptedException (best) or your chosen wrapper
            assertThat(error.get())
                    .isInstanceOfAny(InterruptedException.class, IllegalStateException.class);

            verify(template, times(1)).send(any(Message.class));
        }
    }


    @Test
    void replay_withNullPayload_throwsFromMessageBuilder() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<byte[], byte[]> template = mock(KafkaTemplate.class);

        ReplayItem it = mock(ReplayItem.class);
        when(it.valueBase64()).thenReturn(null);
        when(it.headersBase64()).thenReturn(Map.of());

        ReplayRequest req = mock(ReplayRequest.class);
        when(req.targetTopic()).thenReturn("t");
        when(req.throttlePerSec()).thenReturn(10_000);
        when(req.items()).thenReturn(List.of(it));

        try (MockedStatic<MessageMapper> mm = mockStatic(MessageMapper.class)) {
            mm.when(() -> MessageMapper.filterAllowed(any(), anySet())).thenReturn(Map.of());

            DlqProducerService svc = new DlqProducerService("content-type,correlation-id", template);

            assertThatThrownBy(() -> svc.replay(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Payload must not be null");
        }

        verifyNoInteractions(template);
    }

    @Test
    void replay_propagatesInterrupt_duringSendGet() throws Exception {
        @SuppressWarnings("unchecked")
        KafkaTemplate<byte[], byte[]> template = mock(KafkaTemplate.class);

        CompletableFuture<Object> never = new CompletableFuture<>();
        when(template.send(any(Message.class))).thenReturn((CompletableFuture) never);

        String b64 = Base64.getEncoder().encodeToString("x".getBytes(StandardCharsets.UTF_8));
        ReplayItem item = mock(ReplayItem.class);
        when(item.valueBase64()).thenReturn(b64);
        when(item.headersBase64()).thenReturn(Map.of());

        ReplayRequest req = mock(ReplayRequest.class);
        when(req.targetTopic()).thenReturn("t");
        when(req.throttlePerSec()).thenReturn(10_000); // irrelevant; we interrupt before sleep
        when(req.items()).thenReturn(List.of(item));

        try (MockedStatic<MessageMapper> mm = mockStatic(MessageMapper.class)) {
            mm.when(() -> MessageMapper.filterAllowed(any(), anySet())).thenReturn(Map.of());

            DlqProducerService svc = new DlqProducerService("content-type,correlation-id", template);

            var thrown = new AtomicReference<Throwable>();
            Thread worker = new Thread(() -> {
                try {
                    svc.replay(req); // will block on send(...).get()
                    thrown.set(new AssertionError("Expected InterruptedException to propagate"));
                } catch (Throwable t) {
                    thrown.set(t);
                }
            });

            worker.start();

            Thread.sleep(100);
            worker.interrupt();
            worker.join(2_000);

            assertThat(thrown.get()).isInstanceOf(InterruptedException.class);
            verify(template, times(1)).send(any(Message.class));
        }
    }

}

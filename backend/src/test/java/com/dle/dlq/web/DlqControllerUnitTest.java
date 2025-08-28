package com.dle.dlq.web;

import com.dle.dlq.admin.DlqAdminService;
import com.dle.dlq.kafka.service.consumer.DlqConsumerService;
import com.dle.dlq.dto.MessageDto;
import com.dle.dlq.model.ReplayItem;
import com.dle.dlq.model.ReplayRequest;
import com.dle.dlq.kafka.service.producer.DlqProducerService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DlqControllerUnitTest {

    @Test
    void topics_returnsListFromAdminService() throws Exception {
        DlqAdminService admin = mock(DlqAdminService.class);
        DlqConsumerService consumer = mock(DlqConsumerService.class);
        DlqProducerService producer = mock(DlqProducerService.class);

        when(admin.listDlqTopics()).thenReturn(List.of("a-DLQ", "b-DLQ"));

        DlqController controller = new DlqController(admin, consumer, producer);

        List<String> out = controller.topics();

        assertThat(out).containsExactly("a-DLQ", "b-DLQ");
        verify(admin, times(1)).listDlqTopics();
        verifyNoInteractions(consumer, producer);
    }

    @Test
    void messages_delegatesToConsumer_withTopicAndLimit() {
        DlqAdminService admin = mock(DlqAdminService.class);
        DlqConsumerService consumer = mock(DlqConsumerService.class);
        DlqProducerService producer = mock(DlqProducerService.class);

        var dto1 = new MessageDto("t", 0, 10L, 111L, "k", "v", "dmFsdWU=", Map.of());
        var dto2 = new MessageDto("t", 1, 20L, 222L, "k2", "v2", "dmFsdWUy", Map.of("h","d"));

        when(consumer.fetchLastN("t", 5)).thenReturn(List.of(dto1, dto2));

        DlqController controller = new DlqController(admin, consumer, producer);

        var out = controller.messages("t", 5);

        assertThat(out).containsExactly(dto1, dto2);
        verify(consumer).fetchLastN("t", 5);
        verifyNoInteractions(admin, producer);
    }

    @Test
    void messages_withNullLimit_stillDelegates() {
        DlqAdminService admin = mock(DlqAdminService.class);
        DlqConsumerService consumer = mock(DlqConsumerService.class);
        DlqProducerService producer = mock(DlqProducerService.class);

        var dto = new MessageDto("topicX", 0, 1L, 123L, null, null, null, Map.of());
        when(consumer.fetchLastN("topicX", null)).thenReturn(List.of(dto));

        DlqController controller = new DlqController(admin, consumer, producer);

        var out = controller.messages("topicX", null);

        assertThat(out).containsExactly(dto);
        verify(consumer).fetchLastN("topicX", null);
        verifyNoInteractions(admin, producer);
    }

    @Test
    void replay_delegatesToProducer_andReturnsCount() throws Exception {
        DlqAdminService admin = mock(DlqAdminService.class);
        DlqConsumerService consumer = mock(DlqConsumerService.class);
        DlqProducerService producer = mock(DlqProducerService.class);

        ReplayRequest req = mock(ReplayRequest.class);
        when(producer.replay(req)).thenReturn(3);

        DlqController controller = new DlqController(admin, consumer, producer);

        int count = controller.replay(req);

        assertThat(count).isEqualTo(3);
        verify(producer).replay(req);
        verifyNoInteractions(admin, consumer);
    }

    @Test
    void replay_withNonNullItems_logsSizeAndDelegates() throws Exception {
        DlqAdminService admin = mock(DlqAdminService.class);
        DlqConsumerService consumer = mock(DlqConsumerService.class);
        DlqProducerService producer = mock(DlqProducerService.class);

        ReplayRequest req = mock(ReplayRequest.class);
        when(req.targetTopic()).thenReturn("targetTopic");
        when(req.items()).thenReturn(List.of(mock(ReplayItem.class), mock(ReplayItem.class)));
        when(req.throttlePerSec()).thenReturn(5);
        when(producer.replay(req)).thenReturn(2);

        DlqController controller = new DlqController(admin, consumer, producer);

        int count = controller.replay(req);

        assertThat(count).isEqualTo(2);
        verify(producer).replay(req);
        verifyNoInteractions(admin, consumer);
    }
}

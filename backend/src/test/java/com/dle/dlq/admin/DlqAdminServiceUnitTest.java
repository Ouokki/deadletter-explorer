package com.dle.dlq.admin;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.common.KafkaFuture;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DlqAdminServiceUnitTest {

    @Test
    void listDlqTopics_filtersAndSorts_byPattern() throws Exception {
        AdminClient admin = mock(AdminClient.class);
        ListTopicsResult result = mock(ListTopicsResult.class);
        when(admin.listTopics(any(ListTopicsOptions.class))).thenReturn(result);
        when(result.names()).thenReturn(KafkaFuture.completedFuture(Set.of(
                "orders-DLQ", "payments-DLQ", "regular-topic"
        )));

        try (MockedStatic<AdminClient> st = mockStatic(AdminClient.class)) {
            st.when(() -> AdminClient.create(
                    eq(Map.of("bootstrap.servers", "dummy:9092"))
            )).thenReturn(admin);

            var svc = new DlqAdminService("dummy:9092", ".*-DLQ$");
            List<String> topics = svc.listDlqTopics();

            assertThat(topics).containsExactly("orders-DLQ", "payments-DLQ");
        }

        verify(admin).close();
    }
}

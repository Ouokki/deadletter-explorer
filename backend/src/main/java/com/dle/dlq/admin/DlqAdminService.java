package com.dle.dlq.admin;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
public class DlqAdminService {

    private final String bootstrap;
    private final Pattern dlqPattern;

    public DlqAdminService(@Value("${spring.kafka.bootstrap-servers}") String bootstrap,
            @Value("${dle.dlqPattern:.*-DLQ$}") String pattern) {
        this.bootstrap = bootstrap;
        this.dlqPattern = Pattern.compile(pattern);
    }

    public List<String> listDlqTopics() throws Exception {
        try (var admin = AdminClient.create(Map.of("bootstrap.servers", bootstrap))) {
            var names = admin.listTopics(new ListTopicsOptions().listInternal(false)).names().get();
            return names.stream().filter(t -> dlqPattern.matcher(t).matches()).sorted().toList();
        }
    }
}

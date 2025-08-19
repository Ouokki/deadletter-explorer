package com.dle.dlq.admin;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
public class DlqAdminService {

    private final String bootstrap;
    private final Pattern dlqPattern;

    public DlqAdminService(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrap,
            @Value("${dle.dlqPattern:.*-DLQ$}") String pattern) {
        this.bootstrap = bootstrap;
        this.dlqPattern = Pattern.compile(pattern);
        log.info("DlqAdminService initialized with bootstrap={} and dlqPattern={}", bootstrap, pattern);
    }

    public List<String> listDlqTopics() throws Exception {
        log.debug("Listing topics from Kafka bootstrap={}", bootstrap);

        try (var admin = AdminClient.create(Map.of("bootstrap.servers", bootstrap))) {
            var names = admin.listTopics(new ListTopicsOptions().listInternal(false)).names().get();

            log.info("Discovered {} topics in cluster", names.size());

            var dlqTopics = names.stream()
                    .filter(t -> dlqPattern.matcher(t).matches())
                    .sorted()
                    .toList();

            log.info("Filtered {} DLQ topics matching pattern={}", dlqTopics.size(), dlqPattern);
            if (log.isDebugEnabled()) {
                log.debug("DLQ topics found: {}", dlqTopics);
            }

            return dlqTopics;
        } catch (Exception e) {
            log.error("Failed to list DLQ topics from bootstrap={}", bootstrap, e);
            throw e;
        }
    }
}


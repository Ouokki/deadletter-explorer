package com.dle.dlq.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.dle.dlq.redaction.InMemoryPolicyStore;
import com.dle.dlq.redaction.PolicyStore;

@Configuration
public class RedactionConfig {
    @Bean
    public PolicyStore policyStore() {
        return new InMemoryPolicyStore();
    }
}

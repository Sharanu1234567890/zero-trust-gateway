package com.ztgateway.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_GATEWAY_EVENTS = "gateway-events";
    public static final String TOPIC_THREAT_SCORES = "threat-scores";
    public static final String TOPIC_AUDIT_LOG = "audit-log";
    public static final String TOPIC_CIRCUIT_BREAKER = "circuit-breaker-events";

    @Bean
    public NewTopic gatewayEventsTopic() {
        return TopicBuilder.name(TOPIC_GATEWAY_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic threatScoresTopic() {
        return TopicBuilder.name(TOPIC_THREAT_SCORES)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic auditLogTopic() {
        return TopicBuilder.name(TOPIC_AUDIT_LOG)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic circuitBreakerTopic() {
        return TopicBuilder.name(TOPIC_CIRCUIT_BREAKER)
                .partitions(1)
                .replicas(1)
                .build();
    }
}

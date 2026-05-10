package com.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${app.kafka.topic.payroll}")
    private String payrollTopic;

    @Value("${app.kafka.topic.payroll-log}")
    private String payrollLogTopic;

    @Bean
    public NewTopic payrollTopic() {
        return TopicBuilder.name(payrollTopic)
                .partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic payrollLogTopic() {
        return TopicBuilder.name(payrollLogTopic)
                .partitions(1).replicas(1).build();
    }
}
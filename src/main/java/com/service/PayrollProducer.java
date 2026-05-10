package com.service;

import com.exception.PayrollSerializationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.model.PayrollRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PayrollProducer {

    private static final Logger log = LoggerFactory.getLogger(PayrollProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topic.payroll}")
    private String payrollTopic;

    public PayrollProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
    }

    public void sendPayroll(PayrollRecord payroll) {
        try {
            String json = objectMapper.writeValueAsString(payroll);
            kafkaTemplate.send(payrollTopic, payroll.getCorrelationId(), json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to send | correlationId={} error={}",
                                    payroll.getCorrelationId(), ex.getMessage());
                        } else {
                            log.info("Sent | correlationId={}", payroll.getCorrelationId());
                        }
                    });
        } catch (JsonProcessingException e) {
            log.error("Serialization error | correlationId={} error={}",
                    payroll.getCorrelationId(), e.getMessage());
            throw new PayrollSerializationException(
                    "Failed to serialize payroll for correlationId: " + payroll.getCorrelationId(), e);
        }
    }
}
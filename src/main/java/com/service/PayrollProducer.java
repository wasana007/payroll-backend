package com.service;

import com.contracts.payroll.v1.PayrollEvent;
import com.exception.PayrollSerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class PayrollProducer {

    private static final Logger log =
            LoggerFactory.getLogger(PayrollProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topic.payroll}")
    private String payrollTopic;

    @Value("${app.kafka.topic.payroll-log}")
    private String logTopic;

    public PayrollProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendPayroll(PayrollEvent event) {
        try {
            log.info("[PRODUCER] Sending PayrollEvent: {}", event);

            kafkaTemplate.send(payrollTopic, event.getCorrelationId(), event);

        } catch (Exception e) {
            log.error("[PRODUCER] Failed to send event: {}", e.getMessage());
            throw new PayrollSerializationException("Failed to send payroll event", e);
        }
    }

}
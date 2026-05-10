package com.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.model.PayrollRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PayrollConsumer {

    private static final Logger log = LoggerFactory.getLogger(PayrollConsumer.class);

    private final PayrollService payrollService;
    private final ObjectMapper objectMapper;

    public PayrollConsumer(PayrollService payrollService) {
        this.payrollService = payrollService;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
    }

    @KafkaListener(
            topics = "${app.kafka.topic.payroll}",
            groupId = "${app.kafka.consumer.group-id}"
    )
    public void consume(ConsumerRecord<String, String> consumerRecord) {
        String correlationId = consumerRecord.key();
        String json = consumerRecord.value();

        log.info("Received | correlationId={}", correlationId);

        try {
            PayrollRecord payroll = objectMapper.readValue(json, PayrollRecord.class);
            BigDecimal tax = payroll.getSalary().multiply(BigDecimal.valueOf(0.28));

            log.info("Tax calculated | correlationId={} tax={}", correlationId, tax);

            payrollService.saveCompleted(correlationId);

        } catch (Exception e) {
            log.error("Failed to process | correlationId={} error={}", correlationId, e.getMessage());
            payrollService.saveFailed(correlationId, e.getMessage());
        }
    }
}
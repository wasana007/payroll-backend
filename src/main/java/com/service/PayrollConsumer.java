package com.service;

import com.contracts.payroll.v1.PayrollEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PayrollConsumer {

    private final PayrollService payrollService;

    public PayrollConsumer(PayrollService payrollService) {
        this.payrollService = payrollService;
    }

    @KafkaListener(
            topics = "${app.kafka.topic.payroll}",
            groupId = "${app.kafka.consumer.group-id}"
    )
    public void consume(PayrollEvent event) {

        BigDecimal tax = event.getSalary().multiply(BigDecimal.valueOf(0.28));

        payrollService.saveCompleted(event.getCorrelationId(), tax);
    }
}
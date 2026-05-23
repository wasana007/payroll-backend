package com.service;

import com.contracts.payroll.v1.PayrollEvent;
import org.camunda.bpm.engine.RuntimeService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PayrollConsumer {

    private final RuntimeService runtimeService;

    public PayrollConsumer(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @KafkaListener(
            topics = "${app.kafka.topic.payroll}",
            groupId = "${app.kafka.consumer.group-id}"
    )
    public void consume(PayrollEvent event) {
        runtimeService.startProcessInstanceByKey(
                "payroll-process",
                event.getCorrelationId(),
                Map.of(
                        "correlationId", event.getCorrelationId(),
                        "salary", event.getSalary()
                )
        );
    }
}
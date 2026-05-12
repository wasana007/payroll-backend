package com.service;

import com.contracts.logai.v1.LogEvent;
import com.contracts.payroll.v1.PayrollEvent;
import com.dto.PayrollRequest;
import com.model.PayrollRecord;
import com.repository.PayrollRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PayrollService {

    private final PayrollRepository repository;
    private final PayrollProducer payrollProducer;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topic.payroll-log}")
    private String logTopic;

    public PayrollService(PayrollRepository repository,
                          PayrollProducer payrollProducer,
                          KafkaTemplate<String, Object> kafkaTemplate) {
        this.repository = repository;
        this.payrollProducer = payrollProducer;
        this.kafkaTemplate = kafkaTemplate;
    }

    public PayrollRecord findByCorrelationId(String correlationId) {
        return repository.findByCorrelationId(correlationId).orElse(null);
    }

    public PayrollRecord reportPayroll(PayrollRequest request) {

        validate(request);

        PayrollRecord payroll = new PayrollRecord();
        payroll.setEmployeeId(request.getEmployeeId());
        payroll.setSalary(request.getSalary());
        payroll.setMonth(request.getMonth());
        payroll.setCorrelationId(UUID.randomUUID().toString());
        payroll.setStatus(PayrollRecord.Status.PENDING);
        payroll.setTax(BigDecimal.ZERO);

        PayrollRecord saved = repository.save(payroll);
        payrollProducer.sendPayroll(toEvent(saved));
        sendLog("Payroll created", saved.getCorrelationId());

        return saved;
    }

    public void saveCompleted(String correlationId, BigDecimal tax) {

        repository.findByCorrelationId(correlationId)
                .ifPresent(entry -> {

                    entry.setTax(tax);
                    entry.setStatus(PayrollRecord.Status.COMPLETED);
                    entry.setCompletedAt(LocalDateTime.now());

                    repository.save(entry);

                    sendLog("Payroll completed", correlationId);
                });
    }

    private PayrollEvent toEvent(PayrollRecord payrollRecord) {

        return new PayrollEvent(
                payrollRecord.getCorrelationId(),
                payrollRecord.getEmployeeId(),
                payrollRecord.getSalary(),
                payrollRecord.getMonth(),
                payrollRecord.getStatus().name()
        );
    }

    private void sendLog(String message, String correlationId) {
        LogEvent event = new LogEvent(correlationId, "INFO", message, "PAYROLL_SERVICE");
        kafkaTemplate.send(logTopic, correlationId, event);
    }

    private void validate(PayrollRequest request) {
        if (request.getEmployeeId() == null || request.getEmployeeId().isBlank())
            throw new IllegalArgumentException("Employee ID must not be null or empty");
        if (request.getSalary() == null || request.getSalary().compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Salary must be greater than 0");
        if (request.getMonth() == null || request.getMonth().isBlank())
            throw new IllegalArgumentException("Month is required");
    }
}
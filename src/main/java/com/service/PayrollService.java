package com.service;

import com.dto.PayrollRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.model.LogEvent;
import com.model.PayrollRecord;
import com.repository.PayrollRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PayrollService {

    private static final Logger log = LoggerFactory.getLogger(PayrollService.class);

    private final PayrollRepository repository;
    private final PayrollProducer payrollProducer;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.kafka.topic.payroll-log}")
    private String logTopic;

    public PayrollService(PayrollRepository repository,
                          PayrollProducer payrollProducer,
                          KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.payrollProducer = payrollProducer;
        this.kafkaTemplate = kafkaTemplate;
    }

    public PayrollRecord reportPayroll(PayrollRequest request) {

        validate(request);

        PayrollRecord payroll = new PayrollRecord();
        payroll.setEmployeeId(request.getEmployeeId());
        payroll.setSalary(request.getSalary());
        payroll.setMonth(request.getMonth());
        payroll.setCorrelationId(UUID.randomUUID().toString());
        payroll.setStatus(PayrollRecord.Status.PENDING);

        PayrollRecord saved = repository.save(payroll);

        payrollProducer.sendPayroll(saved);

        log.info("Payroll accepted | correlationId={} employeeId={}",
                saved.getCorrelationId(), saved.getEmployeeId());

        return saved;
    }

    public PayrollRecord findByCorrelationId(String correlationId) {
        return repository.findByCorrelationId(correlationId).orElse(null);
    }

    public void saveCompleted(String correlationId) {
        repository.findByCorrelationId(correlationId).ifPresent(entry -> {
            entry.setStatus(PayrollRecord.Status.COMPLETED);
            entry.setCompletedAt(LocalDateTime.now());
            repository.save(entry);

            sendLogEvent(LogEvent.info(
                    "payroll-service",
                    "Payroll completed | employeeId=" + entry.getEmployeeId(),
                    entry.getEmployeeId()
            ));

            log.info("Completed | correlationId={}", correlationId);
        });
    }

    public void saveFailed(String correlationId, String errorMessage) {
        repository.findByCorrelationId(correlationId).ifPresent(entry -> {
            entry.setStatus(PayrollRecord.Status.FAILED);
            entry.setCompletedAt(LocalDateTime.now());
            repository.save(entry);

            sendLogEvent(LogEvent.error(
                    "payroll-service",
                    "ERROR: " + errorMessage + " | employeeId=" + entry.getEmployeeId(),
                    entry.getEmployeeId()
            ));

            log.error("Failed | correlationId={}", correlationId);
        });
    }

    private void sendLogEvent(LogEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(logTopic, json);
        } catch (Exception e) {
            log.error("Failed to send log event: {}", e.getMessage());
        }
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
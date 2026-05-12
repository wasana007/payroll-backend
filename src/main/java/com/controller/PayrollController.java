package com.controller;

import com.dto.PayrollRequest;
import com.dto.PayrollResponse;
import com.model.PayrollRecord;
import com.service.PayrollService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("${app.api.base-path}")
public class PayrollController {

    private static final Logger log =
            LoggerFactory.getLogger(PayrollController.class);

    private final PayrollService payrollService;

    public PayrollController(PayrollService payrollService) {
        this.payrollService = payrollService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> submitPayroll(
            @RequestBody PayrollRequest request
    ) {
        PayrollRecord saved = payrollService.reportPayroll(request);

        log.info("Payroll received | correlationId={} | employeeId={}",
                saved.getCorrelationId(),
                saved.getEmployeeId());

        return ResponseEntity.accepted().body(
                Map.of(
                        "correlationId", saved.getCorrelationId(),
                        "status", "PENDING"
                )
        );
    }

    @GetMapping("/{correlationId}")
    public ResponseEntity<PayrollResponse> getResult(
            @PathVariable String correlationId) {

        PayrollRecord entry = payrollService.findByCorrelationId(correlationId);

        if (entry == null) {
            log.warn("Payroll not found | correlationId={}", correlationId);
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(toResponse(entry));
    }

    private PayrollResponse toResponse(PayrollRecord entry) {
        PayrollResponse dto = new PayrollResponse();
        dto.setCorrelationId(entry.getCorrelationId());
        dto.setEmployeeId(entry.getEmployeeId());
        dto.setSalary(entry.getSalary());
        dto.setTax(entry.getTax());
        dto.setMonth(entry.getMonth());
        dto.setStatus(entry.getStatus().toString());
        dto.setCreatedAt(entry.getCreatedAt() != null ? entry.getCreatedAt().toString() : "");
        dto.setCompletedAt(entry.getCompletedAt() != null ? entry.getCompletedAt().toString() : "");
        return dto;
    }
}
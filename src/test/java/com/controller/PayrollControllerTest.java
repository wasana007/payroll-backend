package com.controller;

import com.dto.PayrollRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.model.PayrollRecord;
import com.service.PayrollService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "app.api.base-path=/api/v1/payroll")
@Import(com.config.SecurityConfig.class)
@WebMvcTest(PayrollController.class)
@TestPropertySource(properties = "app.api.base-path=/api/v1/payroll")
class PayrollControllerTest {

    private static final String CORRELATION_ID = "corr-123";
    private static final String EMPLOYEE_ID = "emp-456";
    private static final String MONTH = "2026-01";
    private static final BigDecimal SALARY = new BigDecimal("50000.00");
    private static final BigDecimal TAX = new BigDecimal("14000.00");
    private static final String BASE_URL = "/api/v1/payroll";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PayrollService payrollService;

    private PayrollRequest payrollRequest() {
        PayrollRequest request = new PayrollRequest();
        request.setEmployeeId(EMPLOYEE_ID);
        request.setSalary(SALARY);
        request.setMonth(MONTH);
        return request;
    }

    private PayrollRecord payrollRecord(String correlationId,
                                        PayrollRecord.Status status) {
        PayrollRecord payrollRecord = new PayrollRecord();
        payrollRecord.setCorrelationId(correlationId);
        payrollRecord.setEmployeeId(EMPLOYEE_ID);
        payrollRecord.setSalary(SALARY);
        payrollRecord.setTax(TAX);
        payrollRecord.setMonth(MONTH);
        payrollRecord.setStatus(status);
        return payrollRecord;
    }

    @Nested
    @DisplayName("POST /api/v1/payroll")
    class SubmitPayroll {

        @Test
        @DisplayName("returns 202 ACCEPTED with correlationId and PENDING status")
        void returnsAcceptedWithCorrelationId() throws Exception {
            when(payrollService.reportPayroll(any(PayrollRequest.class)))
                    .thenReturn(payrollRecord(CORRELATION_ID, PayrollRecord.Status.PENDING));

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payrollRequest())))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID))
                    .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        @DisplayName("returns correlationId from saved record")
        void returnsCorrelationIdFromSavedRecord() throws Exception {
            when(payrollService.reportPayroll(any(PayrollRequest.class)))
                    .thenReturn(payrollRecord("different-id", PayrollRecord.Status.PENDING));

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(payrollRequest())))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.correlationId").value("different-id"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/payroll/{correlationId}")
    class GetResult {

        @Test
        @DisplayName("returns 200 with PayrollResponse when found")
        void returnsPayrollResponseWhenFound() throws Exception {
            when(payrollService.findByCorrelationId(CORRELATION_ID))
                    .thenReturn(payrollRecord(CORRELATION_ID, PayrollRecord.Status.COMPLETED));

            mockMvc.perform(get(BASE_URL + "/" + CORRELATION_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correlationId").value(CORRELATION_ID))
                    .andExpect(jsonPath("$.employeeId").value(EMPLOYEE_ID))
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }

        @Test
        @DisplayName("returns 404 when correlationId not found")
        void returnsNotFoundWhenMissing() throws Exception {
            when(payrollService.findByCorrelationId(CORRELATION_ID)).thenReturn(null);

            mockMvc.perform(get(BASE_URL + "/" + CORRELATION_ID))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("maps all PayrollRecord fields to PayrollResponse")
        void mapsAllFieldsToResponse() throws Exception {
            PayrollRecord payrollRecord = payrollRecord(CORRELATION_ID, PayrollRecord.Status.COMPLETED);
            payrollRecord.setSalary(SALARY);
            payrollRecord.setTax(TAX);
            payrollRecord.setMonth(MONTH);
            payrollRecord.setCompletedAt(LocalDateTime.of(2026, 1, 1, 10, 5));
            when(payrollService.findByCorrelationId(CORRELATION_ID)).thenReturn(payrollRecord);

            mockMvc.perform(get(BASE_URL + "/" + CORRELATION_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.salary").value(50000.00))
                    .andExpect(jsonPath("$.tax").value(14000.00))
                    .andExpect(jsonPath("$.month").value(MONTH))
                    .andExpect(jsonPath("$.completedAt").isNotEmpty());
        }

        @Test
        @DisplayName("returns empty string for null completedAt")
        void returnsEmptyStringForNullCompletedAt() throws Exception {
            PayrollRecord payrollRecord = payrollRecord(CORRELATION_ID, PayrollRecord.Status.PENDING);
            when(payrollService.findByCorrelationId(CORRELATION_ID)).thenReturn(payrollRecord);

            mockMvc.perform(get(BASE_URL + "/" + CORRELATION_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.completedAt").value(""));
        }

        @Test
        @DisplayName("returns empty string for null createdAt")
        void returnsEmptyStringForNullCreatedAt() throws Exception {
            PayrollRecord payrollRecord = payrollRecord(CORRELATION_ID, PayrollRecord.Status.PENDING);
            when(payrollService.findByCorrelationId(CORRELATION_ID)).thenReturn(payrollRecord);

            mockMvc.perform(get(BASE_URL + "/" + CORRELATION_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.createdAt").value(""));
        }
    }
}
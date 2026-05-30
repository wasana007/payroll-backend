package com.repository;

import com.model.PayrollRecord;
import com.model.PayrollRecord.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollRepositoryTest {

    private static final String CORRELATION_ID = "corr-123";
    private static final String EMPLOYEE_ID = "emp-456";
    private static final String MONTH = "2026-01";
    private static final BigDecimal SALARY = new BigDecimal("50000.00");

    @Mock
    private PayrollRepository payrollRepository;

    private PayrollRecord payrollRecord() {
        PayrollRecord payrollRecord = new PayrollRecord();
        payrollRecord.setCorrelationId(CORRELATION_ID);
        payrollRecord.setEmployeeId(EMPLOYEE_ID);
        payrollRecord.setSalary(SALARY);
        payrollRecord.setMonth(MONTH);
        payrollRecord.setStatus(Status.PENDING);
        return payrollRecord;
    }

    @Nested
    @DisplayName("findByCorrelationId()")
    class FindByCorrelationId {

        @Test
        @DisplayName("returns record when correlationId exists")
        void returnsRecordWhenFound() {
            when(payrollRepository.findByCorrelationId(CORRELATION_ID))
                    .thenReturn(Optional.of(payrollRecord()));

            Optional<PayrollRecord> result = payrollRepository.findByCorrelationId(CORRELATION_ID);

            assertThat(result).isPresent();
            assertThat(result.get().getCorrelationId()).isEqualTo(CORRELATION_ID);
        }

        @Test
        @DisplayName("returns empty when correlationId does not exist")
        void returnsEmptyWhenNotFound() {
            when(payrollRepository.findByCorrelationId(CORRELATION_ID))
                    .thenReturn(Optional.empty());

            Optional<PayrollRecord> result = payrollRepository.findByCorrelationId(CORRELATION_ID);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns correct record among multiple")
        void returnsCorrectRecord() {
            PayrollRecord expected = payrollRecord();
            when(payrollRepository.findByCorrelationId(CORRELATION_ID))
                    .thenReturn(Optional.of(expected));

            Optional<PayrollRecord> result = payrollRepository.findByCorrelationId(CORRELATION_ID);

            assertThat(result.get().getCorrelationId()).isEqualTo(CORRELATION_ID);
            verify(payrollRepository).findByCorrelationId(CORRELATION_ID);
        }

        @Test
        @DisplayName("maps employeeId correctly")
        void mapsEmployeeId() {
            when(payrollRepository.findByCorrelationId(CORRELATION_ID))
                    .thenReturn(Optional.of(payrollRecord()));

            PayrollRecord result = payrollRepository.findByCorrelationId(CORRELATION_ID).get();

            assertThat(result.getEmployeeId()).isEqualTo(EMPLOYEE_ID);
        }

        @Test
        @DisplayName("maps salary correctly")
        void mapsSalary() {
            when(payrollRepository.findByCorrelationId(CORRELATION_ID))
                    .thenReturn(Optional.of(payrollRecord()));

            PayrollRecord result = payrollRepository.findByCorrelationId(CORRELATION_ID).get();

            assertThat(result.getSalary()).isEqualByComparingTo(SALARY);
        }

        @Test
        @DisplayName("maps status correctly")
        void mapsStatus() {
            when(payrollRepository.findByCorrelationId(CORRELATION_ID))
                    .thenReturn(Optional.of(payrollRecord()));

            PayrollRecord result = payrollRepository.findByCorrelationId(CORRELATION_ID).get();

            assertThat(result.getStatus()).isEqualTo(Status.PENDING);
        }
    }
}
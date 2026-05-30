package com.service;

import com.contracts.logai.v1.LogEvent;
import com.contracts.payroll.v1.PayrollEvent;
import com.dto.PayrollRequest;
import com.model.PayrollRecord;
import com.repository.PayrollRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayrollServiceTest {

    private static final String CORRELATION_ID = "corr-123";
    private static final String EMPLOYEE_ID = "emp-456";
    private static final String LOG_TOPIC = "log-events";
    private static final String MONTH = "2026-01";
    private static final String PAYROLL_SERVICE = "PAYROLL_SERVICE";
    private static final BigDecimal SALARY = new BigDecimal("50000.00");
    private static final BigDecimal TAX = new BigDecimal("14000.00");

    @Mock
    private PayrollRepository repository;

    @Mock
    private PayrollProducer payrollProducer;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private PayrollService payrollService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(payrollService, "logTopic", LOG_TOPIC);
    }

    private PayrollRequest buildRequest(String employeeId, String month, BigDecimal salary) {
        PayrollRequest req = new PayrollRequest();
        req.setEmployeeId(employeeId);
        req.setSalary(salary);
        req.setMonth(month);
        return req;
    }

    private PayrollRecord buildRecord() {
        PayrollRecord payrollRecord = new PayrollRecord();
        payrollRecord.setCorrelationId(CORRELATION_ID);
        payrollRecord.setEmployeeId(EMPLOYEE_ID);
        payrollRecord.setMonth(MONTH);
        payrollRecord.setSalary(SALARY);
        payrollRecord.setTax(TAX);
        payrollRecord.setStatus(PayrollRecord.Status.PENDING);
        return payrollRecord;
    }

    @Nested
    @DisplayName("findByCorrelationId")
    class FindByCorrelationId {

        @Test
        @DisplayName("returns the record when found")
        void returnsRecordWhenFound() {
            PayrollRecord payrollRecord = buildRecord();
            when(repository.findByCorrelationId(CORRELATION_ID)).thenReturn(Optional.of(payrollRecord));

            assertThat(payrollService.findByCorrelationId(CORRELATION_ID)).isSameAs(payrollRecord);
        }

        @Test
        @DisplayName("returns null when not found")
        void returnsNullWhenNotFound() {
            when(repository.findByCorrelationId(CORRELATION_ID)).thenReturn(Optional.empty());

            assertThat(payrollService.findByCorrelationId(CORRELATION_ID)).isNull();
        }
    }

    @Nested
    @DisplayName("reportPayroll — happy path")
    class ReportPayrollHappyPath {

        private PayrollRequest validRequest;

        @BeforeEach
        void setUp() {
            validRequest = buildRequest(EMPLOYEE_ID, MONTH, SALARY);
            when(repository.save(any(PayrollRecord.class))).thenReturn(buildRecord());
        }

        @Test
        @DisplayName("persists a new PENDING record with ZERO tax")
        void persistsPendingRecordWithZeroTax() {
            payrollService.reportPayroll(validRequest);

            ArgumentCaptor<PayrollRecord> captor = ArgumentCaptor.forClass(PayrollRecord.class);
            verify(repository).save(captor.capture());

            PayrollRecord persisted = captor.getValue();
            assertThat(persisted.getCorrelationId()).isNotBlank();
            assertThat(persisted.getEmployeeId()).isEqualTo(EMPLOYEE_ID);
            assertThat(persisted.getMonth()).isEqualTo(MONTH);
            assertThat(persisted.getSalary()).isEqualByComparingTo(SALARY);
            assertThat(persisted.getTax()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(persisted.getStatus()).isEqualTo(PayrollRecord.Status.PENDING);
        }

        @Test
        @DisplayName("assigns a fresh UUID as correlationId")
        void assignsFreshUuidAsCorrelationId() {
            payrollService.reportPayroll(validRequest);
            payrollService.reportPayroll(validRequest);

            ArgumentCaptor<PayrollRecord> captor = ArgumentCaptor.forClass(PayrollRecord.class);
            verify(repository, times(2)).save(captor.capture());

            String first = captor.getAllValues().get(0).getCorrelationId();
            String second = captor.getAllValues().get(1).getCorrelationId();
            assertThat(first).isNotEqualTo(second);
        }

        @Test
        @DisplayName("sends a PayrollEvent via the producer")
        void sendsPayrollEventViaProducer() {
            payrollService.reportPayroll(validRequest);

            ArgumentCaptor<PayrollEvent> captor = ArgumentCaptor.forClass(PayrollEvent.class);
            verify(payrollProducer).sendPayroll(captor.capture());

            PayrollEvent event = captor.getValue();
            assertThat(event.getCorrelationId()).isEqualTo(CORRELATION_ID);
            assertThat(event.getEmployeeId()).isEqualTo(EMPLOYEE_ID);
            assertThat(event.getMonth()).isEqualTo(MONTH);
            assertThat(event.getSalary()).isEqualByComparingTo(SALARY);
            assertThat(event.getStatus()).isEqualTo(PayrollRecord.Status.PENDING.name());
        }

        @Test
        @DisplayName("sends a log event to the log topic after saving")
        void sendsLogEventToLogTopic() {
            payrollService.reportPayroll(validRequest);

            ArgumentCaptor<LogEvent> captor = ArgumentCaptor.forClass(LogEvent.class);
            verify(kafkaTemplate).send(eq(LOG_TOPIC), eq(CORRELATION_ID), captor.capture());

            LogEvent log = captor.getValue();
            assertThat(log.getCorrelationId()).isEqualTo(CORRELATION_ID);
            assertThat(log.getMessage()).isEqualTo(PAYROLL_SERVICE);
            assertThat(log.getSource()).isEqualTo(PAYROLL_SERVICE);
        }

        @Test
        @DisplayName("returns the saved record")
        void returnsTheSavedRecord() {
            PayrollRecord result = payrollService.reportPayroll(validRequest);

            assertThat(result.getCorrelationId()).isEqualTo(CORRELATION_ID);
        }

        @Test
        @DisplayName("orchestration: save → sendPayroll → sendLog in order")
        void orchestrationOrder() {
            InOrder inOrder = inOrder(repository, payrollProducer, kafkaTemplate);

            payrollService.reportPayroll(validRequest);

            inOrder.verify(repository).save(any());
            inOrder.verify(payrollProducer).sendPayroll(any());
            inOrder.verify(kafkaTemplate).send(anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("reportPayroll — validation")
    class ReportPayrollValidation {

        @ParameterizedTest(name = "employeeId=\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("rejects blank or null employeeId")
        void rejectsBlankEmployeeId(String employeeId) {
            PayrollRequest req = buildRequest(employeeId, MONTH, SALARY);

            assertThatThrownBy(() -> payrollService.reportPayroll(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Employee ID");

            verifyNoInteractions(repository, payrollProducer, kafkaTemplate);
        }

        @Test
        @DisplayName("rejects null salary")
        void rejectsNullSalary() {
            PayrollRequest req = buildRequest(EMPLOYEE_ID, MONTH, null);

            assertThatThrownBy(() -> payrollService.reportPayroll(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Salary");

            verifyNoInteractions(repository, payrollProducer, kafkaTemplate);
        }

        @ParameterizedTest(name = "salary={0}")
        @ValueSource(strings = {"0", "-1", "-0.01"})
        @DisplayName("rejects non-positive salary")
        void rejectsNonPositiveSalary(String salary) {
            PayrollRequest req = buildRequest(EMPLOYEE_ID, MONTH, new BigDecimal(salary));

            assertThatThrownBy(() -> payrollService.reportPayroll(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Salary");

            verifyNoInteractions(repository, payrollProducer, kafkaTemplate);
        }

        @ParameterizedTest(name = "month=\"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("rejects blank or null month")
        void rejectsBlankMonth(String month) {
            PayrollRequest req = buildRequest(EMPLOYEE_ID, month, SALARY);

            assertThatThrownBy(() -> payrollService.reportPayroll(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Month");

            verifyNoInteractions(repository, payrollProducer, kafkaTemplate);
        }
    }

    @Nested
    @DisplayName("saveCompleted")
    class SaveCompleted {

        @Test
        @DisplayName("updates tax, status, and completedAt when record exists")
        void updatesRecordWhenFound() {
            PayrollRecord existing = buildRecord();
            when(repository.findByCorrelationId(CORRELATION_ID)).thenReturn(Optional.of(existing));

            payrollService.saveCompleted(CORRELATION_ID, TAX);

            ArgumentCaptor<PayrollRecord> captor = ArgumentCaptor.forClass(PayrollRecord.class);
            verify(repository).save(captor.capture());

            PayrollRecord updated = captor.getValue();
            assertThat(updated.getTax()).isEqualByComparingTo(TAX);
            assertThat(updated.getStatus()).isEqualTo(PayrollRecord.Status.COMPLETED);
            assertThat(updated.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("sends a COMPLETED log event to the log topic")
        void sendsCompletedLogEvent() {
            when(repository.findByCorrelationId(CORRELATION_ID))
                    .thenReturn(Optional.of(buildRecord()));

            payrollService.saveCompleted(CORRELATION_ID, TAX);

            ArgumentCaptor<LogEvent> captor = ArgumentCaptor.forClass(LogEvent.class);
            verify(kafkaTemplate).send(eq(LOG_TOPIC), eq(CORRELATION_ID), captor.capture());

            LogEvent log = captor.getValue();
            assertThat(log.getMessage()).isEqualTo(PAYROLL_SERVICE);
            assertThat(log.getCorrelationId()).isEqualTo(CORRELATION_ID);
        }

        @Test
        @DisplayName("does nothing when record is not found")
        void doesNothingWhenNotFound() {
            when(repository.findByCorrelationId(CORRELATION_ID)).thenReturn(Optional.empty());

            payrollService.saveCompleted(CORRELATION_ID, TAX);

            verify(repository, never()).save(any());
            verifyNoInteractions(kafkaTemplate);
        }

        @Test
        @DisplayName("completedAt is set to a time close to now")
        void completedAtIsSetToNow() {
            PayrollRecord existing = buildRecord();
            when(repository.findByCorrelationId(CORRELATION_ID)).thenReturn(Optional.of(existing));

            LocalDateTime before = LocalDateTime.now();
            payrollService.saveCompleted(CORRELATION_ID, TAX);
            LocalDateTime after = LocalDateTime.now();

            assertThat(existing.getCompletedAt())
                    .isAfterOrEqualTo(before)
                    .isBeforeOrEqualTo(after);
        }
    }
}
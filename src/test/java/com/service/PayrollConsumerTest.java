package com.service;

import com.contracts.payroll.v1.PayrollEvent;
import org.camunda.bpm.engine.RuntimeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayrollConsumerTest {

    private static final String CORRELATION_ID = "corr-123";
    private static final BigDecimal SALARY = new BigDecimal("50000.00");

    @Mock
    private RuntimeService runtimeService;

    @InjectMocks
    private PayrollConsumer payrollConsumer;

    private PayrollEvent buildEvent(String correlationId, BigDecimal salary) {
        return new PayrollEvent(correlationId, "EMP-1", salary, "2024-01", "PENDING");
    }

    @Nested
    @DisplayName("consume — happy path")
    class ConsumeHappyPath {

        @Test
        @DisplayName("starts a process instance with the correct process definition key")
        void startsProcessWithCorrectKey() {
            PayrollEvent event = buildEvent(CORRELATION_ID, SALARY);

            payrollConsumer.consume(event);

            verify(runtimeService).startProcessInstanceByKey(
                    eq("payroll-process"), anyString(), any(Map.class));
        }

        @Test
        @DisplayName("uses correlationId as the business key")
        void usesCorrelationIdAsBusinessKey() {
            PayrollEvent event = buildEvent(CORRELATION_ID, SALARY);

            payrollConsumer.consume(event);

            verify(runtimeService).startProcessInstanceByKey(
                    anyString(), eq(CORRELATION_ID), any(Map.class));
        }

        @Test
        @DisplayName("passes correlationId and salary in the process variables map")
        void passesCorrectVariables() {
            PayrollEvent event = buildEvent(CORRELATION_ID, SALARY);

            payrollConsumer.consume(event);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(runtimeService).startProcessInstanceByKey(
                    anyString(), anyString(), captor.capture());

            assertThat(captor.getValue())
                    .containsEntry("correlationId", CORRELATION_ID)
                    .containsEntry("salary", SALARY);
        }

        @Test
        @DisplayName("passes exactly two variables — no extras")
        void passesExactlyTwoVariables() {
            PayrollEvent event = buildEvent(CORRELATION_ID, SALARY);

            payrollConsumer.consume(event);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(runtimeService).startProcessInstanceByKey(
                    anyString(), anyString(), captor.capture());

            assertThat(captor.getValue()).hasSize(2);
        }

        @Test
        @DisplayName("startProcessInstanceByKey is called exactly once per event")
        void calledExactlyOnce() {
            PayrollEvent event = buildEvent(CORRELATION_ID, SALARY);

            payrollConsumer.consume(event);

            verify(runtimeService, times(1))
                    .startProcessInstanceByKey(anyString(), anyString(), any(Map.class));
        }

        @Test
        @DisplayName("each event starts an independent process instance with its own correlationId")
        void eachEventStartsIndependentProcess() {
            PayrollEvent first = buildEvent("corr-001", new BigDecimal("3000"));
            PayrollEvent second = buildEvent("corr-002", new BigDecimal("6000"));

            payrollConsumer.consume(first);
            payrollConsumer.consume(second);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> varsCaptor = ArgumentCaptor.forClass(Map.class);
            ArgumentCaptor<String> bizKeyCaptor = ArgumentCaptor.forClass(String.class);
            verify(runtimeService, times(2))
                    .startProcessInstanceByKey(
                            anyString(), bizKeyCaptor.capture(), varsCaptor.capture());

            assertThat(bizKeyCaptor.getAllValues()).containsExactly("corr-001", "corr-002");
            assertThat(varsCaptor.getAllValues().get(0))
                    .containsEntry("salary", new BigDecimal("3000"));
            assertThat(varsCaptor.getAllValues().get(1))
                    .containsEntry("salary", new BigDecimal("6000"));
        }
    }

    @Nested
    @DisplayName("consume — error path")
    class ConsumeErrorPath {

        @Test
        @DisplayName("propagates exception thrown by RuntimeService without swallowing it")
        void propagatesRuntimeServiceException() {
            doThrow(new RuntimeException("Camunda engine unavailable"))
                    .when(runtimeService)
                    .startProcessInstanceByKey(anyString(), anyString(), any(Map.class));

            PayrollEvent event = buildEvent(CORRELATION_ID, SALARY);

            assertThatThrownBy(() -> payrollConsumer.consume(event))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Camunda engine unavailable");
        }
    }
}
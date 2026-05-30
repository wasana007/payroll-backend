package com.delegate;

import com.service.PayrollService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaveCompletedDelegateTest {

    private static final String VAR_CORRELATION_ID = "correlationId";
    private static final String CORRELATION_ID = "corr-123";
    private static final String VAR_TAX = "tax";
    private static final BigDecimal TAX = new BigDecimal("14000.00");

    @Mock
    private DelegateExecution execution;

    @Mock
    private PayrollService payrollService;

    @InjectMocks
    private SaveCompletedDelegate saveCompletedDelegate;

    @Nested
    @DisplayName("execute()")
    class Execute {

        @Test
        @DisplayName("reads correlationId from execution variables")
        void readsCorrelationId() {
            when(execution.getVariable(VAR_CORRELATION_ID)).thenReturn(CORRELATION_ID);
            when(execution.getVariable(VAR_TAX)).thenReturn(TAX);

            saveCompletedDelegate.execute(execution);

            verify(execution).getVariable(VAR_CORRELATION_ID);
        }

        @Test
        @DisplayName("reads tax from execution variables")
        void readsTax() {
            when(execution.getVariable(VAR_CORRELATION_ID)).thenReturn(CORRELATION_ID);
            when(execution.getVariable(VAR_TAX)).thenReturn(TAX);

            saveCompletedDelegate.execute(execution);

            verify(execution).getVariable(VAR_TAX);
        }

        @Test
        @DisplayName("delegates to payrollService with correlationId and tax")
        void delegatesToService() {
            when(execution.getVariable(VAR_CORRELATION_ID)).thenReturn(CORRELATION_ID);
            when(execution.getVariable(VAR_TAX)).thenReturn(TAX);

            saveCompletedDelegate.execute(execution);

            verify(payrollService).saveCompleted(CORRELATION_ID, TAX);
        }
    }
}
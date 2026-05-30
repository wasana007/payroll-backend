package com.delegate;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalculateTaxDelegateTest {

    private static final String VAR_SALARY = "salary";
    private static final String VAR_TAX = "tax";
    private static final BigDecimal SALARY = new BigDecimal("50000.00");
    private static final BigDecimal SALARY_LARGE = new BigDecimal("100000");
    private static final BigDecimal SALARY_DECIMAL = new BigDecimal("1000.50");
    private static final BigDecimal EXPECTED_TAX = new BigDecimal("14000.00");
    private static final BigDecimal EXPECTED_TAX_DECIMAL = new BigDecimal("280.14");

    @Mock
    private DelegateExecution execution;

    @InjectMocks
    private CalculateTaxDelegate calculateTaxDelegate;

    @Nested
    @DisplayName("execute()")
    class Execute {

        @Test
        @DisplayName("calculates 28% tax on salary")
        void calculatesTax() {
            when(execution.getVariable(VAR_SALARY)).thenReturn(SALARY);

            calculateTaxDelegate.execute(execution);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(execution).setVariable(eq(VAR_TAX), captor.capture());

            assertThat((BigDecimal) captor.getValue()).isEqualByComparingTo(EXPECTED_TAX);
        }

        @Test
        @DisplayName("sets tax variable on execution")
        void setsTaxVariable() {
            when(execution.getVariable(VAR_SALARY)).thenReturn(SALARY_LARGE);

            calculateTaxDelegate.execute(execution);

            verify(execution).setVariable(eq(VAR_TAX), any());
        }

        @Test
        @DisplayName("calculates tax correctly for zero salary")
        void calculatesZeroTaxForZeroSalary() {
            when(execution.getVariable(VAR_SALARY)).thenReturn(BigDecimal.ZERO);

            calculateTaxDelegate.execute(execution);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(execution).setVariable(eq(VAR_TAX), captor.capture());

            assertThat((BigDecimal) captor.getValue()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("calculates tax correctly for decimal salary")
        void calculatesTaxForDecimalSalary() {
            when(execution.getVariable(VAR_SALARY)).thenReturn(SALARY_DECIMAL);

            calculateTaxDelegate.execute(execution);

            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(execution).setVariable(eq(VAR_TAX), captor.capture());

            assertThat((BigDecimal) captor.getValue()).isEqualByComparingTo(EXPECTED_TAX_DECIMAL);
        }

        @Test
        @DisplayName("reads salary from execution variables")
        void readsSalaryFromExecution() {
            when(execution.getVariable(VAR_SALARY)).thenReturn(SALARY);

            calculateTaxDelegate.execute(execution);

            verify(execution).getVariable(VAR_SALARY);
        }
    }
}
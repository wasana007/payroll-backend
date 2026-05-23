package com.delegate;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CalculateTaxDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        BigDecimal salary = (BigDecimal) execution.getVariable("salary");
        BigDecimal tax = salary.multiply(BigDecimal.valueOf(0.28));
        execution.setVariable("tax", tax);
    }
}
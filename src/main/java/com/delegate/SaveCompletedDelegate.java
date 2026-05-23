package com.delegate;

import com.service.PayrollService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class SaveCompletedDelegate implements JavaDelegate {

    private final PayrollService payrollService;

    public SaveCompletedDelegate(PayrollService payrollService) {
        this.payrollService = payrollService;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String correlationId = (String) execution.getVariable("correlationId");
        BigDecimal tax = (BigDecimal) execution.getVariable("tax");
        payrollService.saveCompleted(correlationId, tax);
    }
}
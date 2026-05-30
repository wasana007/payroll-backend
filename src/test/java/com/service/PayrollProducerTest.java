package com.service;

import com.contracts.payroll.v1.PayrollEvent;
import com.exception.PayrollSerializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayrollProducerTest {

    private static final String LOG_TOPIC = "log-events";
    private static final String CORRELATION_ID = "corr-123";
    private static final String PAYROLL_TOPIC = "payroll-topic";

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private PayrollEvent payrollEvent;

    @InjectMocks
    private PayrollProducer payrollProducer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(payrollProducer, "logTopic", LOG_TOPIC);
        ReflectionTestUtils.setField(payrollProducer, "payrollTopic", PAYROLL_TOPIC);
    }

    @Test
    @DisplayName("sendPayroll: sends event to the correct topic with correlationId as key")
    void sendPayroll_sendsToCorrectTopicAndKey() {
        when(payrollEvent.getCorrelationId()).thenReturn(CORRELATION_ID);

        payrollProducer.sendPayroll(payrollEvent);

        verify(kafkaTemplate, times(1)).send(PAYROLL_TOPIC, CORRELATION_ID, payrollEvent);
    }

    @Test
    @DisplayName("sendPayroll: resolves correlationId from the event itself")
    void sendPayroll_usesCorrelationIdFromEvent() {
        String differentId = "corr-999";
        when(payrollEvent.getCorrelationId()).thenReturn(differentId);

        payrollProducer.sendPayroll(payrollEvent);

        verify(kafkaTemplate).send(PAYROLL_TOPIC, differentId, payrollEvent);
    }

    @Test
    @DisplayName("sendPayroll: does not interact with logTopic")
    void sendPayroll_doesNotSendToLogTopic() {
        when(payrollEvent.getCorrelationId()).thenReturn(CORRELATION_ID);

        payrollProducer.sendPayroll(payrollEvent);

        verify(kafkaTemplate, never()).send(eq(LOG_TOPIC), any(), any());
    }

    @Test
    @DisplayName("sendPayroll: wraps KafkaTemplate exception in PayrollSerializationException")
    void sendPayroll_whenKafkaThrows_throwsPayrollSerializationException() {
        when(payrollEvent.getCorrelationId()).thenReturn(CORRELATION_ID);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Kafka broker unavailable"));

        assertThatThrownBy(() -> payrollProducer.sendPayroll(payrollEvent))
                .isInstanceOf(PayrollSerializationException.class)
                .hasMessageContaining("Failed to send payroll event")
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("sendPayroll: original exception is preserved as cause")
    void sendPayroll_preservesOriginalExceptionAsCause() {
        RuntimeException rootCause = new RuntimeException("broker down");
        when(payrollEvent.getCorrelationId()).thenReturn(CORRELATION_ID);
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenThrow(rootCause);

        assertThatThrownBy(() -> payrollProducer.sendPayroll(payrollEvent))
                .isInstanceOf(PayrollSerializationException.class)
                .cause()
                .isSameAs(rootCause);
    }

    @Test
    @DisplayName("sendPayroll: kafkaTemplate.send is never called when getCorrelationId throws")
    void sendPayroll_whenCorrelationIdThrows_kafkaIsNeverInvoked() {
        when(payrollEvent.getCorrelationId()).thenThrow(new RuntimeException("corrupt event"));

        assertThatThrownBy(() -> payrollProducer.sendPayroll(payrollEvent))
                .isInstanceOf(PayrollSerializationException.class);

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }
}
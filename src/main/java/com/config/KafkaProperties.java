package com.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka")
public class KafkaProperties {

    private Topic topic = new Topic();
    private Consumer consumer = new Consumer();

    public Topic getTopic() {
        return topic;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public static class Topic {
        private String payroll;
        private String payrollLog;

        public String getPayroll() {
            return payroll;
        }

        public void setPayroll(String payroll) {
            this.payroll = payroll;
        }

        public String getPayrollLog() {
            return payrollLog;
        }

        public void setPayrollLog(String payrollLog) {
            this.payrollLog = payrollLog;
        }
    }

    public static class Consumer {
        private String groupId;

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }
    }
}
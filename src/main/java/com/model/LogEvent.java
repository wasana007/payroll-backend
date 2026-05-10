package com.model;

import org.springframework.boot.logging.LogLevel;

import java.time.LocalDateTime;

public class LogEvent {

    private String source;
    private LogLevel level;
    private String message;
    private String employeeId;
    private LocalDateTime timestamp;

    public LogEvent() {}

    public LogEvent(String source, LogLevel level, String message, String employeeId) {
        this.source = source;
        this.level = level;
        this.message = message;
        this.employeeId = employeeId;
        this.timestamp = LocalDateTime.now();
    }

    public static LogEvent info(String source, String message, String employeeId) {
        return new LogEvent(source, LogLevel.INFO, message, employeeId);
    }

    public static LogEvent warn(String source, String message, String employeeId) {
        return new LogEvent(source, LogLevel.WARN, message, employeeId);
    }

    public static LogEvent error(String source, String message, String employeeId) {
        return new LogEvent(source, LogLevel.ERROR, message, employeeId);
    }

    public String getSource() { return source; }
    public LogLevel getLevel() { return level; }
    public String getMessage() { return message; }
    public String getEmployeeId() { return employeeId; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
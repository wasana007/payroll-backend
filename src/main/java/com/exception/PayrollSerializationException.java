package com.exception;
 
public class PayrollSerializationException extends RuntimeException {

    public PayrollSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
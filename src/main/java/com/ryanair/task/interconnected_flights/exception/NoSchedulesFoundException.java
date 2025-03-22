package com.ryanair.task.interconnected_flights.exception;

public class NoSchedulesFoundException extends RuntimeException {
    public NoSchedulesFoundException(String message) {
        super(message);
    }
}

package com.smartload.exception;

public class TooManyOrdersException extends RuntimeException {

    public TooManyOrdersException(String message) {
        super(message);
    }
}

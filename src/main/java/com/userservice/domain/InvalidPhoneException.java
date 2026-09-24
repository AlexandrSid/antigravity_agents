package com.userservice.domain;

public class InvalidPhoneException extends RuntimeException {

    public InvalidPhoneException() {
    }

    public InvalidPhoneException(String message) {
        super(message);
    }
}

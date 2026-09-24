package com.userservice.domain;

public class DuplicatePhoneException extends RuntimeException {

    public DuplicatePhoneException() {
    }

    public DuplicatePhoneException(String message) {
        super(message);
    }
}

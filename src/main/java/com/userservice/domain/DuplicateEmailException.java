package com.userservice.domain;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException() {
    }

    public DuplicateEmailException(String message) {
        super(message);
    }
}

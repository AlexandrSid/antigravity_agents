package com.userservice.domain;

public class ParentNotFoundException extends RuntimeException {

    public ParentNotFoundException() {
    }

    public ParentNotFoundException(String message) {
        super(message);
    }
}

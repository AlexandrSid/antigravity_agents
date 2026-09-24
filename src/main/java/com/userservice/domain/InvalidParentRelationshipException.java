package com.userservice.domain;

public class InvalidParentRelationshipException extends RuntimeException {

    public InvalidParentRelationshipException() {
    }

    public InvalidParentRelationshipException(String message) {
        super(message);
    }
}

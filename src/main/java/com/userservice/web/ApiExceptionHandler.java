package com.userservice.web;

import com.userservice.domain.DuplicateEmailException;
import com.userservice.domain.DuplicatePhoneException;
import com.userservice.domain.InvalidParentRelationshipException;
import com.userservice.domain.InvalidPhoneException;
import com.userservice.domain.ParentNotFoundException;
import com.userservice.domain.UserNotFoundException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException exception) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @ExceptionHandler(ParentNotFoundException.class)
    public ProblemDetail handleParentNotFound(ParentNotFoundException exception) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ProblemDetail handleDuplicateEmail(DuplicateEmailException exception) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @ExceptionHandler(DuplicatePhoneException.class)
    public ProblemDetail handleDuplicatePhone(DuplicatePhoneException exception) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @ExceptionHandler(InvalidPhoneException.class)
    public ProblemDetail handleInvalidPhone(InvalidPhoneException exception) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @ExceptionHandler(InvalidParentRelationshipException.class)
    public ProblemDetail handleInvalidParentRelationship(InvalidParentRelationshipException exception) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMalformedJson(HttpMessageNotReadableException exception) {
        throw new UnsupportedOperationException("Not implemented");
    }
}

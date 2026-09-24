package com.userservice.web;

import com.userservice.domain.DuplicateEmailException;
import com.userservice.domain.DuplicatePhoneException;
import com.userservice.domain.InvalidParentRelationshipException;
import com.userservice.domain.InvalidPhoneException;
import com.userservice.domain.ParentNotFoundException;
import com.userservice.domain.UserNotFoundException;
import com.userservice.dto.InvalidParam;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URI;
import java.util.List;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "urn:user-service:error:user-not-found", "User Not Found", exception.getMessage());
    }

    @ExceptionHandler(ParentNotFoundException.class)
    public ProblemDetail handleParentNotFound(ParentNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "urn:user-service:error:parent-not-found", "Parent User Not Found", exception.getMessage());
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ProblemDetail handleDuplicateEmail(DuplicateEmailException exception) {
        return problem(HttpStatus.CONFLICT, "urn:user-service:error:duplicate-email", "Email Conflict", exception.getMessage());
    }

    @ExceptionHandler(DuplicatePhoneException.class)
    public ProblemDetail handleDuplicatePhone(DuplicatePhoneException exception) {
        return problem(HttpStatus.CONFLICT, "urn:user-service:error:duplicate-phone", "Phone Number Conflict", exception.getMessage());
    }

    @ExceptionHandler(InvalidPhoneException.class)
    public ProblemDetail handleInvalidPhone(InvalidPhoneException exception) {
        return problem(HttpStatus.BAD_REQUEST, "urn:user-service:error:validation-failed", "Validation Failed", exception.getMessage());
    }

    @ExceptionHandler(InvalidParentRelationshipException.class)
    public ProblemDetail handleInvalidParentRelationship(InvalidParentRelationshipException exception) {
        return problem(HttpStatus.BAD_REQUEST, "urn:user-service:error:invalid-parent-relation", "Invalid Parent Relationship", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "urn:user-service:error:validation-failed",
                "Validation Failed",
                "Request validation failed"
        );
        List<InvalidParam> invalidParams = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new InvalidParam(error.getField(), error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage()))
                .toList();
        problem.setProperty("invalidParams", invalidParams);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMalformedJson(HttpMessageNotReadableException exception) {
        return problem(HttpStatus.BAD_REQUEST, "urn:user-service:error:malformed-json", "Malformed JSON Payload", "Malformed JSON payload");
    }

    private ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail == null || detail.isBlank() ? title : detail);
        problem.setType(URI.create(type));
        problem.setTitle(title);
        problem.setInstance(URI.create(currentInstance()));
        return problem;
    }

    private String currentInstance() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return request.getRequestURI();
        }
        return "/";
    }
}

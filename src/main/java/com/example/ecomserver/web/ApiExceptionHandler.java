package com.example.ecomserver.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the exceptions thrown by {@link EcommerceRestController} to clean HTTP responses
 * (RFC 7807 problem+json) instead of a 500 with a stack trace.
 */
@RestControllerAdvice(assignableTypes = EcommerceRestController.class)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Bad input from the caller, e.g. an unsupported site key. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * The scrape itself failed - the upstream site blocked us, changed markup, or (commonly on
     * corporate networks) TLS interception broke certificate validation. This is an upstream
     * problem, not a server bug, so report it as 502 Bad Gateway.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleUpstreamFailure(IllegalStateException ex) {
        log.warn("Upstream scrape failed: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }
}

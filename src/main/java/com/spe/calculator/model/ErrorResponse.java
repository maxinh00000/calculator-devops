package com.spe.calculator.model;

import java.time.Instant;

/** Structured error body returned with HTTP 400. */
public record ErrorResponse(String timestamp, int status, String error, String message, String path) {

    public static ErrorResponse badRequest(String message, String path) {
        return new ErrorResponse(Instant.now().toString(), 400, "Bad Request", message, path);
    }
}

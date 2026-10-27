package com.spe.calculator.exception;

import com.spe.calculator.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Converts every validation problem into HTTP 400 with a JSON ErrorResponse. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DivisionByZeroException.class)
    public ResponseEntity<ErrorResponse> handleDivideByZero(DivisionByZeroException ex, HttpServletRequest req) {
        return badRequest(ex.getMessage(), req);
    }

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<ErrorResponse> handleInvalid(InvalidInputException ex, HttpServletRequest req) {
        return badRequest(ex.getMessage(), req);
    }

    /** e.g. /api/v1/add?a=2  (b missing) */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissing(MissingServletRequestParameterException ex,
                                                       HttpServletRequest req) {
        return badRequest("Missing required parameter '" + ex.getParameterName() + "'", req);
    }

    /** e.g. /api/v1/add?a=abc&b=3 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest req) {
        return badRequest("Parameter '" + ex.getName() + "' must be a number, got '" + ex.getValue() + "'", req);
    }

    private ResponseEntity<ErrorResponse> badRequest(String message, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.badRequest(message, req.getRequestURI()));
    }
}

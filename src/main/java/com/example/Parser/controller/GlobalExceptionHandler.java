package com.example.Parser.controller;

import com.example.Parser.exception.ParseTimeoutException;
import com.example.Parser.exception.VacancyParseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleBadRequest(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();

        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));

        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Ошибка валидации запроса"
        );
        detail.setProperty("errors", errors);

        return ResponseEntity.badRequest().body(detail);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NoSuchElementException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ParseTimeoutException.class)
    public ResponseEntity<ProblemDetail> handleTimeout(ParseTimeoutException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.GATEWAY_TIMEOUT,
                exception.getMessage()
        );
        detail.setProperty("jobId", exception.getJobId());

        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(detail);
    }

    @ExceptionHandler(VacancyParseException.class)
    public ResponseEntity<ProblemDetail> handleParseError(VacancyParseException exception) {
        return problem(HttpStatus.BAD_GATEWAY, exception.getMessage());
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, message));
    }
}

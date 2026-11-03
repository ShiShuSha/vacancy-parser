package com.example.Parser.exception;

public class VacancyParseException extends RuntimeException {

    public VacancyParseException(String message) {
        super(message);
    }

    public VacancyParseException(String message, Throwable cause) {
        super(message, cause);
    }
}

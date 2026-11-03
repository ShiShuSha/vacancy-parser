package com.example.Parser.exception;

public class ParseTimeoutException extends RuntimeException {

    private final String jobId;

    public ParseTimeoutException(String message, String jobId) {
        super(message);
        this.jobId = jobId;
    }

    public String getJobId() {
        return jobId;
    }
}

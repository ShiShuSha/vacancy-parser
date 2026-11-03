package com.example.Parser.dto;

import jakarta.validation.constraints.NotBlank;

public class ParseRequest {

    @NotBlank(message = "URL не должен быть пустым")
    private String url;

    public ParseRequest() {
    }

    public ParseRequest(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}

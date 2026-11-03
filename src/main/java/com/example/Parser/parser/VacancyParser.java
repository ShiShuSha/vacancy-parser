package com.example.Parser.parser;

import com.example.Parser.model.Vacancy;

public interface VacancyParser {

    boolean supports(String url);

    Vacancy parse(String html, String url);
}

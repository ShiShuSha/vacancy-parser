package com.example.Parser.controller;

import com.example.Parser.dto.PageResponse;
import com.example.Parser.model.Vacancy;
import com.example.Parser.service.VacancyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnswerController {

    private final VacancyService vacancyService;

    public AnswerController(VacancyService vacancyService) {
        this.vacancyService = vacancyService;
    }

    /**
     * Получение результатов из H2 с фильтрацией, пагинацией и сортировкой.
     * Фильтрация и сортировка выполняются через parallelStream.
     */
    @GetMapping("/answer")
    public PageResponse<Vacancy> answer(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "publicationDate") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) String title
    ) {
        return vacancyService.getAnswer(
                page,
                size,
                sortBy,
                direction,
                city,
                company,
                title
        );
    }
}

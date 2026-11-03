package com.example.Parser.controller;

import com.example.Parser.dto.ParseJobView;
import com.example.Parser.dto.ParseRequest;
import com.example.Parser.job.ParseJob;
import com.example.Parser.model.Vacancy;
import com.example.Parser.service.ParseJobService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/vacancies")
public class VacancyController {

    private final ParseJobService parseJobService;

    public VacancyController(ParseJobService parseJobService) {
        this.parseJobService = parseJobService;
    }

    /**
     * Синхронный разбор: клиент получает уже сохранённую вакансию.
     * Внутри запрос выполняется в кастомном пуле потоков.
     */
    @PostMapping("/parse")
    public ResponseEntity<Vacancy> parse(@Valid @RequestBody ParseRequest request) {
        Vacancy vacancy = parseJobService.submitAndWait(request.getUrl());
        return ResponseEntity.ok(vacancy);
    }

    /**
     * Асинхронный разбор: сразу возвращается id задачи.
     */
    @PostMapping("/parse/async")
    public ResponseEntity<ParseJobView> parseAsync(@Valid @RequestBody ParseRequest request) {
        ParseJob job = parseJobService.submit(request.getUrl());
        return ResponseEntity.accepted().body(job.toView());
    }

    @GetMapping("/jobs")
    public List<ParseJobView> jobs() {
        return parseJobService.list();
    }

    @GetMapping("/jobs/{id}")
    public ParseJobView job(@PathVariable String id) {
        return parseJobService.get(id);
    }
}

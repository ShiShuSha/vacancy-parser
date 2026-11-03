package com.example.Parser.service;

import com.example.Parser.dto.PageResponse;
import com.example.Parser.model.Vacancy;
import com.example.Parser.repository.VacancyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VacancyServiceTest {

    @Mock
    private VacancyRepository vacancyRepository;

    private VacancyService vacancyService;

    @BeforeEach
    void setUp() {
        vacancyService = new VacancyService(vacancyRepository);
    }

    @Test
    void filtersByCityAndPaginatesAfterParallelStream() {
        when(vacancyRepository.findAll()).thenReturn(List.of(
                vacancy("Java Developer", "Acme", "от 100 000 ₽", "Москва", "2026-01-10"),
                vacancy("System Analyst", "Neolant", "от 150 000 ₽", "Калининград", "2026-02-10"),
                vacancy("Java Team Lead", "Acme", "от 200 000 ₽", "Москва", "2025-12-01")
        ));

        PageResponse<Vacancy> response = vacancyService.getAnswer(
                0, 1, "title", "asc", "Москва", null, null
        );

        assertEquals(2, response.totalElements());
        assertEquals(2, response.totalPages());
        assertEquals(1, response.content().size());
        assertEquals("Java Developer", response.content().getFirst().getTitle());
        assertEquals(Map.of("city", "Москва"), response.filters());
    }

    @Test
    void filtersByCompanyIgnoreCaseAndSortsBySalaryDescending() {
        when(vacancyRepository.findAll()).thenReturn(List.of(
                vacancy("Java Developer", "Acme", "от 100 000 ₽", "Москва", "2026-01-10"),
                vacancy("System Analyst", "Neolant", "от 150 000 ₽", "Калининград", "2026-02-10"),
                vacancy("Java Team Lead", "Acme", "от 200 000 ₽", "Москва", "2025-12-01")
        ));

        PageResponse<Vacancy> response = vacancyService.getAnswer(
                0, 10, "salary", "desc", null, "acme", null
        );

        assertEquals(2, response.totalElements());
        assertEquals("Java Team Lead", response.content().get(0).getTitle());
        assertEquals("Java Developer", response.content().get(1).getTitle());
        assertEquals(Map.of("company", "acme"), response.filters());
    }

    @Test
    void returnsEmptyPageWhenNothingMatches() {
        when(vacancyRepository.findAll()).thenReturn(List.of(
                vacancy("Java Developer", "Acme", "от 100 000 ₽", "Москва", "2026-01-10")
        ));

        PageResponse<Vacancy> response = vacancyService.getAnswer(
                0, 10, "publicationDate", "desc", null, null, "Python"
        );

        assertEquals(0, response.totalElements());
        assertEquals(0, response.totalPages());
        assertTrue(response.content().isEmpty());
    }

    @Test
    void normalizesNegativePageAndTooSmallSize() {
        when(vacancyRepository.findAll()).thenReturn(List.of(
                vacancy("Java Developer", "Acme", "от 100 000 ₽", "Москва", "2026-01-10")
        ));

        PageResponse<Vacancy> response = vacancyService.getAnswer(
                -5, 0, "unknown", "asc", null, null, null
        );

        assertEquals(0, response.page());
        assertEquals(1, response.size());
        assertEquals(1, response.totalElements());
        assertEquals("publicationDate", response.sortBy());
    }

    private Vacancy vacancy(
            String title,
            String company,
            String salary,
            String city,
            String publicationDate
    ) {
        return new Vacancy(
                title,
                company,
                salary,
                "Требования",
                city,
                publicationDate,
                "https://example.com/" + title.replace(' ', '-').toLowerCase()
        );
    }
}

package com.example.Parser.dto;

import java.util.List;
import java.util.Map;

/**
 * Результат постраничной выдачи. Фильтрация и сортировка выполняются
 * через parallelStream в {@code VacancyService}.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String sortBy,
        String direction,
        Map<String, String> filters
) {
}

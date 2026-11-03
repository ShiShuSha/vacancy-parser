package com.example.Parser.dto;

import com.example.Parser.model.Vacancy;

import java.time.Instant;

/**
 * Состояние фоновой задачи разбора вакансии.
 */
public record ParseJobView(
        String id,
        String url,
        String status,
        int attempts,
        String error,
        Vacancy result,
        Instant createdAt,
        Instant updatedAt,
        Instant nextRetryAt
) {
}

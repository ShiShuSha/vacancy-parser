package com.example.Parser.repository;

import com.example.Parser.model.Vacancy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VacancyRepository extends JpaRepository<Vacancy, Long> {

    Optional<Vacancy> findByUrl(String url);
}

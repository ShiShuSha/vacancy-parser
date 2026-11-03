package com.example.Parser.service;

import com.example.Parser.dto.PageResponse;
import com.example.Parser.model.Vacancy;
import com.example.Parser.repository.VacancyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class VacancyService {

    private static final Pattern SALARY_NUMBER = Pattern.compile("\\d[\\d\\s]*");
    private static final int MAX_PAGE_SIZE = 100;

    private final VacancyRepository vacancyRepository;

    public VacancyService(VacancyRepository vacancyRepository) {
        this.vacancyRepository = vacancyRepository;
    }

    @Transactional
    public Vacancy save(Vacancy vacancy) {
        return vacancyRepository.save(vacancy);
    }

    /**
     * Сохраняет новую вакансию или обновляет уже сохранённую с тем же URL.
     */
    @Transactional
    public Vacancy upsert(Vacancy parsedVacancy) {
        return vacancyRepository.findByUrl(parsedVacancy.getUrl())
                .map(existing -> {
                    parsedVacancy.setId(existing.getId());
                    return vacancyRepository.save(parsedVacancy);
                })
                .orElseGet(() -> vacancyRepository.save(parsedVacancy));
    }

    @Transactional(readOnly = true)
    public Optional<Vacancy> findById(Long id) {
        return vacancyRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Vacancy> findAll() {
        return vacancyRepository.findAll();
    }

    /**
     * Читает вакансии из H2, фильтрует и сортирует их через parallelStream,
     * затем применяет пагинацию. Фильтры необязательны и сравниваются
     * без учёта регистра по вхождению подстроки.
     */
    @Transactional(readOnly = true)
    public PageResponse<Vacancy> getAnswer(
            int page,
            int size,
            String sortBy,
            String direction,
            String city,
            String company,
            String title
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String safeSortBy = normalizeSortBy(sortBy);
        boolean descending = "desc".equalsIgnoreCase(direction);

        String cityFilter = normalizeFilter(city);
        String companyFilter = normalizeFilter(company);
        String titleFilter = normalizeFilter(title);

        Map<String, String> filters = new LinkedHashMap<>();

        if (cityFilter != null) {
            filters.put("city", cityFilter);
        }

        if (companyFilter != null) {
            filters.put("company", companyFilter);
        }

        if (titleFilter != null) {
            filters.put("title", titleFilter);
        }

        Comparator<Vacancy> comparator = comparatorFor(safeSortBy);

        if (descending) {
            comparator = comparator.reversed();
        }

        Predicate<Vacancy> filter = vacancy -> matches(vacancy.getCity(), cityFilter)
                && matches(vacancy.getCompany(), companyFilter)
                && matches(vacancy.getTitle(), titleFilter);

        List<Vacancy> filtered = vacancyRepository.findAll()
                .parallelStream()
                .filter(filter)
                .sorted(comparator)
                .toList();

        long totalElements = filtered.size();
        int totalPages = (int) Math.ceil(totalElements / (double) safeSize);
        long fromLong = (long) safePage * safeSize;
        int from = (int) Math.min(fromLong, totalElements);
        int to = Math.min(from + safeSize, filtered.size());

        return new PageResponse<>(
                filtered.subList(from, to),
                safePage,
                safeSize,
                totalElements,
                totalPages,
                safeSortBy,
                descending ? "desc" : "asc",
                Map.copyOf(filters)
        );
    }

    private String normalizeFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean matches(String value, String filter) {
        if (filter == null) {
            return true;
        }

        return value != null
                && value.toLowerCase(Locale.ROOT)
                .contains(filter.toLowerCase(Locale.ROOT));
    }

    private String normalizeSortBy(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "publicationDate";
        }

        String normalized = sortBy.toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "id", "title", "company", "salary", "city" -> normalized;
            case "publicationdate", "date" -> "publicationDate";
            default -> "publicationDate";
        };
    }

    private Comparator<Vacancy> comparatorFor(String sortBy) {
        return switch (sortBy) {
            case "id" -> Comparator.comparing(
                    Vacancy::getId,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "title" -> Comparator.comparing(
                    Vacancy::getTitle,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
            );
            case "company" -> Comparator.comparing(
                    Vacancy::getCompany,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
            );
            case "salary" -> Comparator.comparing(
                    vacancy -> salaryValue(vacancy.getSalary()),
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
            case "city" -> Comparator.comparing(
                    Vacancy::getCity,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
            );
            default -> Comparator.comparing(
                    Vacancy::getPublicationDate,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
        };
    }

    private Long salaryValue(String salary) {
        if (salary == null || salary.isBlank()) {
            return null;
        }

        Matcher matcher = SALARY_NUMBER.matcher(salary);

        if (!matcher.find()) {
            return null;
        }

        try {
            return Long.parseLong(matcher.group().replaceAll("\\D", ""));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}

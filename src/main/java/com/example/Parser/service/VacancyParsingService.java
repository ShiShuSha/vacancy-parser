package com.example.Parser.service;

import com.example.Parser.exception.VacancyParseException;
import com.example.Parser.model.Vacancy;
import com.example.Parser.parser.VacancyParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Locale;

@Service
public class VacancyParsingService {

    private static final Logger log = LoggerFactory.getLogger(VacancyParsingService.class);

    private final List<VacancyParser> parsers;
    private final HtmlFetchService htmlFetchService;
    private final VacancyService vacancyService;

    public VacancyParsingService(
            List<VacancyParser> parsers,
            HtmlFetchService htmlFetchService,
            VacancyService vacancyService
    ) {
        this.parsers = parsers;
        this.htmlFetchService = htmlFetchService;
        this.vacancyService = vacancyService;
    }

    /**
     * Полный цикл: проверить URL, получить HTML в память, выбрать парсер,
     * извлечь данные и сохранить/обновить вакансию в H2.
     * Метод выполняется в потоке кастомного ExecutorService.
     */
    public Vacancy parseAndSave(String url) {
        String normalizedUrl = validateUrl(url);
        VacancyParser parser = findParser(normalizedUrl);

        log.info("Начинаю разбор {} парсером {}", normalizedUrl, parser.getClass().getSimpleName());

        String html;

        try {
            html = htmlFetchService.fetchHtmlBlocking(normalizedUrl);
        } catch (Exception exception) {
            throw new VacancyParseException(
                    "Не удалось загрузить страницу " + normalizedUrl + ": "
                            + rootMessage(exception),
                    exception
            );
        }

        if (html == null || html.isBlank()) {
            throw new VacancyParseException("Сайт вернул пустую страницу: " + normalizedUrl);
        }

        Vacancy vacancy;

        try {
            vacancy = parser.parse(html, normalizedUrl);
        } catch (Exception exception) {
            throw new VacancyParseException(
                    "Не удалось разобрать HTML вакансии: " + rootMessage(exception),
                    exception
            );
        }

        if (vacancy.getTitle() == null || vacancy.getTitle().isBlank()) {
            throw new VacancyParseException(
                    "На странице не найдено название вакансии. "
                            + "Возможно, сайт вернул капчу или изменил разметку: " + normalizedUrl
            );
        }

        Vacancy saved = vacancyService.upsert(vacancy);
        log.info("Вакансия сохранена: id={}, title={}", saved.getId(), saved.getTitle());

        return saved;
    }

    private VacancyParser findParser(String url) {
        return parsers.parallelStream()
                .filter(parser -> parser.supports(url))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Неподдерживаемый сайт: " + url
                                + ". Поддерживаются career.habr.com и zarplata.ru"
                ));
    }

    private String validateUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL не должен быть пустым");
        }

        String normalized = url.trim();

        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme();

            if (scheme == null
                    || (!scheme.toLowerCase(Locale.ROOT).equals("http")
                    && !scheme.toLowerCase(Locale.ROOT).equals("https"))) {
                throw new IllegalArgumentException("URL должен начинаться с http:// или https://");
            }

            if (uri.getHost() == null) {
                throw new IllegalArgumentException("В URL не найден домен: " + url);
            }

            return normalized;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Некорректный URL: " + url);
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;

        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }

        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }
}

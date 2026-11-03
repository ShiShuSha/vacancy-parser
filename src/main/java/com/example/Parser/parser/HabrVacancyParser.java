package com.example.Parser.parser;

import com.example.Parser.model.Vacancy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class HabrVacancyParser implements VacancyParser {

    private static final String SOURCE = "career.habr.com/vacancies/";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean supports(String url) {
        return url != null && url.contains(SOURCE);
    }

    @Override
    public Vacancy parse(String html, String url) {
        Document document = Jsoup.parse(html);
        JsonNode jobPosting = findJobPosting(document);

        String title = "";
        String company = "";
        String salary = "";
        String requirements = "";
        String city = "";
        String publicationDate = "";

        if (jobPosting != null) {
            title = jobPosting.path("title").asText("");

            company = jobPosting
                    .path("hiringOrganization")
                    .path("name")
                    .asText("");

            salary = formatSalary(jobPosting.path("baseSalary"));
            city = extractCity(jobPosting.path("jobLocation"));

            requirements = DescriptionSectionExtractor.extractRequirements(
                    jobPosting.path("description").asText("")
            );

            publicationDate = normalizeDate(
                    jobPosting.path("datePosted").asText("")
            );
        }

        if (title.isBlank()) {
            title = firstText(document, "h1.section-title", "h1");
        }

        if (company.isBlank()) {
            company = firstText(document, ".vacancy-company__title a", ".company_name");
        }

        if (salary.isBlank()) {
            salary = firstText(document, ".vacancy-salary", ".basic-salary");
        }

        if (requirements.isBlank()) {
            requirements = extractRequirementsFromHeader(document);
        }

        if (requirements.isBlank()) {
            requirements = DescriptionSectionExtractor.extractRequirements(
                    firstHtml(document, ".vacancy-description__text", ".vacancy-description")
            );
        }

        if (city.isBlank()) {
            city = firstText(document,
                    ".vacancy-header__location",
                    ".vacancy-company__location"
            );
        }

        if (publicationDate.isBlank()) {
            publicationDate = findDate(document);
        }

        return new Vacancy(
                title,
                company,
                salary,
                requirements,
                city,
                publicationDate,
                url
        );
    }

    /**
     * В шапке вакансии Habr есть блок "Требования" с чипами:
     * специализация, грейд и технологии. У части вакансий такого блока нет
     * в JSON-LD-описании, поэтому берём значения из разметки страницы.
     */
    private String extractRequirementsFromHeader(Document document) {
        for (Element heading : document.select("h2, h3, h4, h5")) {
            if (!"требования".equals(normalizeHeading(heading.text()))) {
                continue;
            }

            Element section = heading.closest(".content-section");

            if (section == null) {
                section = heading.parent();

                if (section == null) {
                    continue;
                }
            }

            List<String> values = new ArrayList<>();

            for (Element chip : section.select(".chip-with-icon__text, .chip-without-icon__text")) {
                addUnique(values, chip.text());
            }

            if (!values.isEmpty()) {
                return String.join("; ", values);
            }
        }

        return "";
    }

    private void addUnique(List<String> values, String rawValue) {
        String value = normalizeText(rawValue);

        if (!value.isBlank() && !values.contains(value)) {
            values.add(value);
        }
    }

    private String normalizeHeading(String value) {
        return normalizeText(value)
                .toLowerCase(Locale.ROOT)
                .replaceFirst(":$", "")
                .trim();
    }

    private JsonNode findJobPosting(Document document) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            try {
                JsonNode json = objectMapper.readTree(script.data());

                if (json == null) {
                    continue;
                }

                if (isJobPosting(json)) {
                    return json;
                }

                if (json.isArray()) {
                    for (JsonNode item : json) {
                        if (isJobPosting(item)) {
                            return item;
                        }
                    }
                }
            } catch (Exception ignored) {
                // Встречаются script-блоки с невалидным JSON — просто пропускаем их.
            }
        }

        return null;
    }

    private boolean isJobPosting(JsonNode node) {
        JsonNode type = node.path("@type");

        if (type.isArray()) {
            for (JsonNode item : type) {
                if ("JobPosting".equals(item.asText())) {
                    return true;
                }
            }
            return false;
        }

        return "JobPosting".equals(type.asText());
    }

    private String formatSalary(JsonNode baseSalary) {
        if (baseSalary == null || baseSalary.isMissingNode() || baseSalary.isNull()) {
            return "";
        }

        JsonNode value = baseSalary.path("value");
        String currency = currencySymbol(baseSalary.path("currency").asText(""));

        String min = value.path("minValue").asText("");
        String max = value.path("maxValue").asText("");
        String single = value.path("value").asText("");

        if (!min.isBlank() && !max.isBlank()) {
            return "от " + formatMoney(min) + " до " + formatMoney(max) + " " + currency;
        }

        if (!min.isBlank()) {
            return "от " + formatMoney(min) + " " + currency;
        }

        if (!max.isBlank()) {
            return "до " + formatMoney(max) + " " + currency;
        }

        if (!single.isBlank()) {
            return formatMoney(single) + " " + currency;
        }

        return "";
    }

    private String formatMoney(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "";
        }

        try {
            BigDecimal amount = new BigDecimal(rawValue.trim());
            return NumberFormat.getIntegerInstance(Locale.US)
                    .format(amount)
                    .replace(',', ' ');
        } catch (NumberFormatException exception) {
            return rawValue.trim();
        }
    }

    private String currencySymbol(String currency) {
        if (currency == null || currency.isBlank()) {
            return "₽";
        }

        return switch (currency.toUpperCase(Locale.ROOT)) {
            case "RUR", "RUB" -> "₽";
            case "USD" -> "$";
            case "EUR" -> "€";
            default -> currency;
        };
    }

    private String extractCity(JsonNode locations) {
        Set<String> cities = new LinkedHashSet<>();

        if (locations == null || locations.isMissingNode() || locations.isNull()) {
            return "";
        }

        if (locations.isArray()) {
            for (JsonNode location : locations) {
                addCity(location, cities);
            }
        } else {
            addCity(locations, cities);
        }

        return String.join(", ", cities);
    }

    private void addCity(JsonNode location, Set<String> cities) {
        JsonNode address = location.path("address");

        if (address.isTextual()) {
            addCityValue(address.asText(""), cities);
            return;
        }

        String city = address.path("addressLocality").asText("");

        if (city.isBlank()) {
            city = address.path("addressRegion").asText("");
        }

        if (city.isBlank()) {
            city = location.path("name").asText("");
        }

        addCityValue(city, cities);
    }

    private void addCityValue(String city, Set<String> cities) {
        String normalized = normalizeText(city);

        if (!normalized.isBlank()) {
            cities.add(normalized);
        }
    }

    private String firstText(Document document, String... selectors) {
        for (String selector : selectors) {
            Element element = document.selectFirst(selector);

            if (element != null) {
                String text = normalizeText(element.text());

                if (!text.isBlank()) {
                    return text;
                }
            }
        }

        return "";
    }

    private String firstHtml(Document document, String... selectors) {
        for (String selector : selectors) {
            Element element = document.selectFirst(selector);

            if (element != null && !element.text().isBlank()) {
                return element.html();
            }
        }

        return "";
    }

    private String findDate(Document document) {
        Element element = document.selectFirst("time[datetime]");

        if (element != null && !element.attr("datetime").isBlank()) {
            return normalizeDate(element.attr("datetime"));
        }

        Element meta = document.selectFirst("meta[property=article:published_time]");

        if (meta != null) {
            return normalizeDate(meta.attr("content"));
        }

        return "";
    }

    private String normalizeDate(String value) {
        String normalized = normalizeText(value);

        if (normalized.matches("^\\d{4}-\\d{2}-\\d{2}.*")) {
            return normalized.substring(0, 10);
        }

        return normalized;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
}

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
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class ZarplataVacancyParser implements VacancyParser {

    private static final String SOURCE = "zarplata.ru/vacancy/";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean supports(String url) {
        return url != null && (
                url.contains(SOURCE)
                        || url.contains("zarplata.ru/vacancies/")
        );
    }

    @Override
    public Vacancy parse(String html, String url) {
        Document document = Jsoup.parse(html);
        JsonNode jobPosting = findJobPosting(document);

        String title = firstText(document,
                "[data-qa=vacancy-title]",
                "h1"
        );

        String company = firstText(document,
                "[data-qa=vacancy-company__details]",
                "[data-qa=vacancy-company-name]",
                ".vacancy-company-details"
        );

        String salary = firstText(document,
                "[data-qa=vacancy-salary]",
                ".compensation-labels"
        );

        String city = firstText(document,
                "[data-qa=vacancy-view-location]",
                "[data-qa=vacancy-view-raw-address]"
        );

        String publicationDate = "";

        if (jobPosting != null) {
            if (title.isBlank()) {
                title = jobPosting.path("title").asText("");
            }

            if (company.isBlank()) {
                company = jobPosting
                        .path("hiringOrganization")
                        .path("name")
                        .asText("");
            }

            if (salary.isBlank()) {
                salary = formatSalary(jobPosting.path("baseSalary"));
            }

            if (city.isBlank()) {
                city = extractCity(jobPosting.path("jobLocation"));
            }

            publicationDate = normalizeDate(
                    jobPosting.path("datePosted").asText("")
            );
        }

        if (publicationDate.isBlank()) {
            publicationDate = findDate(document);
        }

        String descriptionHtml = firstHtml(document,
                "[data-qa=vacancy-description]",
                ".vacancy-description"
        );

        if (descriptionHtml.isBlank() && jobPosting != null) {
            descriptionHtml = jobPosting.path("description").asText("");
        }

        String requirements = DescriptionSectionExtractor
                .extractRequirements(descriptionHtml);

        return new Vacancy(
                normalizeText(title),
                normalizeText(company),
                normalizeText(salary),
                requirements,
                normalizeCity(city),
                publicationDate,
                url
        );
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
                // Некорректные JSON-LD блоки пропускаем.
            }
        }

        return null;
    }

    private boolean isJobPosting(JsonNode node) {
        return "JobPosting".equals(node.path("@type").asText());
    }

    private String formatSalary(JsonNode baseSalary) {
        if (baseSalary == null || baseSalary.isMissingNode() || baseSalary.isNull()) {
            return "";
        }

        JsonNode value = baseSalary.path("value");
        String currency = currencySymbol(baseSalary.path("currency").asText(""));

        String min = value.path("minValue").asText("");
        String max = value.path("maxValue").asText("");

        if (!min.isBlank() && !max.isBlank()) {
            return "от " + formatMoney(min) + " до " + formatMoney(max) + " " + currency;
        }

        if (!min.isBlank()) {
            return "от " + formatMoney(min) + " " + currency;
        }

        if (!max.isBlank()) {
            return "до " + formatMoney(max) + " " + currency;
        }

        return "";
    }

    private String formatMoney(String rawValue) {
        try {
            BigDecimal amount = new BigDecimal(rawValue.trim());
            return NumberFormat.getIntegerInstance(Locale.US)
                    .format(amount)
                    .replace(',', ' ');
        } catch (NumberFormatException exception) {
            return rawValue == null ? "" : rawValue.trim();
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

    private String extractCity(JsonNode location) {
        Set<String> cities = new LinkedHashSet<>();

        if (location == null || location.isMissingNode() || location.isNull()) {
            return "";
        }

        if (location.isArray()) {
            for (JsonNode item : location) {
                addCity(item, cities);
            }
        } else {
            addCity(location, cities);
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

    private String normalizeCity(String value) {
        String city = normalizeText(value);

        if (city.isBlank()) {
            return "";
        }

        int commaIndex = city.indexOf(',');

        if (commaIndex > 0) {
            city = city.substring(0, commaIndex).trim();
        }

        return city;
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
        Element jsonDate = document.selectFirst("time[datetime]");

        if (jsonDate != null && !jsonDate.attr("datetime").isBlank()) {
            return normalizeDate(jsonDate.attr("datetime"));
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

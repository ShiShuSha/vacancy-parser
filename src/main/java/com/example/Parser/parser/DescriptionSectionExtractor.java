package com.example.Parser.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Извлекает раздел "Требования" из HTML-описания вакансии.
 * Habr и Zarplata используют разную разметку, поэтому поддерживаются
 * и список ul/li, и набор абзацев.
 *
 * <p>Если отдельного раздела "Требования" нет (такое встречается у Habr),
 * требования собираются из строки вида
 * {@code Навыки: ... Квалификация: ... Специализации: ...}.</p>
 */
final class DescriptionSectionExtractor {

    private static final Set<String> SECTION_HEADINGS = Set.of(
            "требования",
            "обязанности",
            "будет плюсом",
            "условия",
            "мы предлагаем",
            "что предлагаем",
            "о компании",
            "квалификация"
    );

    private static final Pattern LABELED_VALUES = Pattern.compile(
            "(?iu)(навыки|квалификация|специализации)\\s*:\\s*([^:]+?)(?=\\s*(?:навыки|квалификация|специализации)\\s*:|$)"
    );

    private static final Pattern STARTS_WITH_LABEL = Pattern.compile(
            "(?iu)^\\s*(навыки|квалификация|специализации)\\s*:"
    );

    private DescriptionSectionExtractor() {
    }

    static String extractRequirements(String descriptionHtml) {
        if (descriptionHtml == null || descriptionHtml.isBlank()) {
            return "";
        }

        Document description = Jsoup.parse(descriptionHtml);
        Element heading = findHeading(description, "требования");

        if (heading != null) {
            List<String> requirements = new ArrayList<>();

            Element sibling = heading.nextElementSibling();
            collect(sibling, requirements);

            if (requirements.isEmpty() && heading.parent() != null) {
                collect(heading.parent().nextElementSibling(), requirements);
            }

            if (!requirements.isEmpty()) {
                return String.join("; ", requirements);
            }
        }

        return extractLabeledValues(description);
    }

    /**
     * Ищет абзац с подписями "Навыки", "Квалификация", "Специализации"
     * и возвращает значения в порядке из шапки вакансии:
     * специализация, квалификация, затем технологии.
     */
    private static String extractLabeledValues(Document description) {
        Elements candidates = description.select("p, li, div, span");

        for (Element candidate : candidates) {
            String text = normalizeSpaces(candidate.text());

            if (!STARTS_WITH_LABEL.matcher(text).find()) {
                continue;
            }

            Map<String, String> values = parseLabeledValues(text);

            if (values.isEmpty()) {
                continue;
            }

            List<String> requirements = new ArrayList<>();

            addValue(values.get("специализации"), requirements);
            addValue(values.get("квалификация"), requirements);

            String skills = values.get("навыки");

            if (skills != null) {
                for (String skill : skills.split("\\s*,\\s*")) {
                    addRequirement(skill, requirements);
                }
            }

            if (!requirements.isEmpty()) {
                return String.join("; ", requirements);
            }
        }

        return "";
    }

    private static Map<String, String> parseLabeledValues(String text) {
        Map<String, String> values = new LinkedHashMap<>();
        Matcher matcher = LABELED_VALUES.matcher(text);

        while (matcher.find()) {
            String label = matcher.group(1).toLowerCase(Locale.ROOT);
            String value = matcher.group(2)
                    .replace('\u00A0', ' ')
                    .replaceAll("\\s+", " ")
                    .replaceFirst("[.,;\\s]+$", "")
                    .trim();

            if (!value.isBlank()) {
                values.putIfAbsent(label, value);
            }
        }

        return values;
    }

    private static void addValue(String value, List<String> requirements) {
        if (value != null) {
            addRequirement(value, requirements);
        }
    }

    private static Element findHeading(Document document, String expectedHeading) {
        Elements candidates = document.select("strong, b, h2, h3, h4, h5, h6");

        for (Element candidate : candidates) {
            if (normalizeHeading(candidate.text()).equals(expectedHeading)) {
                return candidate;
            }
        }

        return null;
    }

    private static void collect(Element element, List<String> requirements) {
        Element current = element;

        while (current != null) {
            if (isSectionHeading(current)) {
                break;
            }

            Elements listItems = current.select("li");

            if (!listItems.isEmpty()) {
                for (Element listItem : listItems) {
                    addRequirement(listItem.text(), requirements);
                }
            } else {
                addRequirement(current.text(), requirements);
            }

            current = current.nextElementSibling();
        }
    }

    private static boolean isSectionHeading(Element element) {
        String text = normalizeHeading(element.text());

        for (String heading : SECTION_HEADINGS) {
            if (text.equals(heading) || text.startsWith(heading + " ")) {
                return true;
            }
        }

        return false;
    }

    private static void addRequirement(String value, List<String> requirements) {
        if (value == null) {
            return;
        }

        String text = value
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim()
                .replaceFirst("^[·•\\-–—]+\\s*", "")
                .replaceFirst(";+$", "")
                .trim();

        if (!text.isEmpty() && !isSectionHeadingText(text)) {
            requirements.add(text);
        }
    }

    private static boolean isSectionHeadingText(String text) {
        String normalized = normalizeHeading(text);

        for (String heading : SECTION_HEADINGS) {
            if (normalized.equals(heading)) {
                return true;
            }
        }

        return false;
    }

    private static String normalizeHeading(String value) {
        return normalizeSpaces(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll(":$", "")
                .trim();
    }

    private static String normalizeSpaces(String value) {
        return value
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }
}

package com.example.Parser.parser;

import com.example.Parser.model.Vacancy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

//Юнит-тесты парсеров на небольших HTML-фрагментах, повторяющих структуруреальных страниц.
class VacancyParserTest {

    private static final String HABR_HTML = """
            <!doctype html>
            <html>
            <head>
              <script type="application/ld+json">
              {
                "@context": "https://schema.org",
                "@type": "JobPosting",
                "title": "Системный аналитик",
                "hiringOrganization": { "name": "ГК Неолант" },
                "baseSalary": {
                  "currency": "RUR",
                  "value": { "minValue": "150000", "maxValue": "250000" }
                },
                "jobLocation": [
                  { "address": { "addressLocality": "Калининград" } },
                  { "address": { "addressLocality": "Ставрополь" } }
                ],
                "datePosted": "2026-10-13T09:00:00+03:00",
                "description": "<h2>Требования</h2><ul><li>Опыт работы системным аналитиком не менее 5 лет</li><li>Знание UML и BPMN</li></ul><h2>Обязанности</h2><ul><li>Сбор требований</li></ul>"
              }
              </script>
            </head>
            <body>
              <h1 class="section-title">Системный аналитик</h1>
            </body>
            </html>
            """;

    private static final String ZARPLATA_HTML = """
            <!doctype html>
            <html>
            <body>
              <h1 data-qa="vacancy-title">Бухгалтер</h1>
              <div data-qa="vacancy-company__details">ООО Запсибгазпром-Газификация</div>
              <div data-qa="vacancy-salary">от 60 000 ₽ за месяц на руки</div>
              <div data-qa="vacancy-view-location">Тюмень</div>
              <div data-qa="vacancy-description">
                <h3>Требования</h3>
                <ul>
                  <li>Профессиональный пользователь ПК, 1С, Excel, Контур</li>
                  <li>Опыт работы с первичной документацией</li>
                </ul>
                <h3>Обязанности</h3>
                <ul>
                  <li>Ведение бухгалтерского учёта</li>
                </ul>
              </div>
              <time datetime="2026-10-29"></time>
            </body>
            </html>
            """;

    private static final String HABR_HEADER_CHIPS_HTML = """
            <!doctype html>
            <html>
            <head>
              <script type="application/ld+json">
              {
                "@context": "https://schema.org",
                "@type": "JobPosting",
                "title": "Go-разработчик, Товародвижение и коммерция",
                "hiringOrganization": { "name": "Ozon Tech" },
                "baseSalary": {
                  "currency": "RUR",
                  "value": { "minValue": "365000" }
                },
                "jobLocation": [ { "address": { "addressLocality": "Москва" } } ],
                "datePosted": "2026-10-23",
                "description": "<p>Привет! Мы – команда товародвижения и коммерции Ozon fresh.</p><p><strong>Наш стек</strong></p><ul><li>Go, PostgreSQL, Kafka.</li></ul><p><strong>Вы будете</strong></p><ul><li>Развивать существующие сервисы.</li></ul>"
              }
              </script>
            </head>
            <body>
              <h1 class="page-title__title">Go-разработчик, Товародвижение и коммерция</h1>
              <div class="content-section content-section--appearance-chips">
                <div class="content-section__header">
                  <h2 class="content-section__title">Требования</h2>
                </div>
                <div class="vacancy-meta">
                  <div class="chip-with-icon__text">Бэкенд разработчик</div>
                  <div class="chip-with-icon__text">Middle</div>
                  <div class="chip-without-icon__text">Golang</div>
                  <div class="chip-without-icon__text">PostgreSQL</div>
                  <div class="chip-without-icon__text">Elasticsearch</div>
                  <div class="chip-without-icon__text">ClickHouse</div>
                  <div class="chip-without-icon__text">Redis</div>
                  <div class="chip-without-icon__text">Apache Kafka</div>
                  <div class="chip-without-icon__text">Prometheus</div>
                  <div class="chip-without-icon__text">Grafana</div>
                  <div class="chip-without-icon__text">CI/CD</div>
                  <div class="chip-without-icon__text">Kubernetes</div>
                </div>
              </div>
              <div class="content-section content-section--appearance-chips">
                <div class="content-section__header">
                  <h2 class="content-section__title">Условия</h2>
                </div>
                <div class="vacancy-meta">
                  <div class="chip-with-icon__text">Можно удалённо</div>
                  <div class="chip-with-icon__text">Москва</div>
                </div>
              </div>
            </body>
            </html>
            """;

    private static final String HABR_LABELED_VALUES_HTML = """
            <!doctype html>
            <html>
            <head>
              <script type="application/ld+json">
              {
                "@context": "https://schema.org",
                "@type": "JobPosting",
                "title": "Go-разработчик",
                "description": "<p>Навыки: Golang, PostgreSQL, Elasticsearch, ClickHouse, Redis, Apache Kafka, Prometheus, Grafana, CI/CD, Kubernetes. Квалификация: Middle. Специализации: Бэкенд разработчик.</p><p>Привет! Мы – команда товародвижения.</p><p><strong>Вы будете</strong></p><ul><li>Развивать существующие сервисы.</li></ul>"
              }
              </script>
            </head>
            <body>
              <h1 class="page-title__title">Go-разработчик</h1>
            </body>
            </html>
            """;

    @Test
    void parsesHabrJobPosting() {
        Vacancy vacancy = new HabrVacancyParser()
                .parse(HABR_HTML, "https://career.habr.com/vacancies/1000169168");

        assertTrue(vacancy.getTitle().contains("Системный аналитик"));
        assertTrue(vacancy.getCompany().contains("Неолант"));
        assertTrue(vacancy.getSalary().contains("150 000"));
        assertTrue(vacancy.getSalary().contains("250 000"));
        assertTrue(vacancy.getCity().contains("Калининград"));
        assertTrue(vacancy.getCity().contains("Ставрополь"));
        assertTrue(vacancy.getPublicationDate().equals("2026-10-13"));
        assertTrue(vacancy.getRequirements().contains("Опыт работы системным аналитиком"));
        assertFalse(vacancy.getRequirements().contains("Сбор требований"));
    }

    @Test
    void parsesZarplataPage() {
        Vacancy vacancy = new ZarplataVacancyParser()
                .parse(ZARPLATA_HTML, "https://zarplata.ru/vacancy/138963182");

        assertTrue(vacancy.getTitle().contains("Бухгалтер"));
        assertTrue(vacancy.getCompany().contains("Запсибгазпром-Газификация"));
        assertTrue(vacancy.getSalary().contains("60 000"));
        assertTrue(vacancy.getCity().contains("Тюмень"));
        assertTrue(vacancy.getPublicationDate().equals("2026-10-29"));
        assertTrue(vacancy.getRequirements().contains("Профессиональный пользователь ПК"));
        assertFalse(vacancy.getRequirements().contains("Ведение бухгалтерского учёта"));
    }

    @Test
    void parsesHabrRequirementsFromHeaderChips() {
        Vacancy vacancy = new HabrVacancyParser()
                .parse(HABR_HEADER_CHIPS_HTML, "https://career.habr.com/vacancies/1000167228");

        String requirements = vacancy.getRequirements();

        assertTrue(requirements.contains("Бэкенд разработчик"));
        assertTrue(requirements.contains("Middle"));
        assertTrue(requirements.contains("Golang"));
        assertTrue(requirements.contains("PostgreSQL"));
        assertTrue(requirements.contains("Elasticsearch"));
        assertTrue(requirements.contains("ClickHouse"));
        assertTrue(requirements.contains("Redis"));
        assertTrue(requirements.contains("Apache Kafka"));
        assertTrue(requirements.contains("Prometheus"));
        assertTrue(requirements.contains("Grafana"));
        assertTrue(requirements.contains("CI/CD"));
        assertTrue(requirements.contains("Kubernetes"));
        assertFalse(requirements.contains("Можно удалённо"));
        assertFalse(requirements.contains("Развивать существующие сервисы"));
    }

    @Test
    void parsesHabrRequirementsFromLabeledValues() {
        Vacancy vacancy = new HabrVacancyParser()
                .parse(HABR_LABELED_VALUES_HTML, "https://career.habr.com/vacancies/1000167229");

        String requirements = vacancy.getRequirements();

        assertTrue(requirements.contains("Бэкенд разработчик"));
        assertTrue(requirements.contains("Middle"));
        assertTrue(requirements.contains("Golang"));
        assertTrue(requirements.contains("Kubernetes"));
        assertFalse(requirements.contains("Привет!"));
        assertFalse(requirements.contains("Развивать существующие сервисы"));
    }

    @Test
    void supportsOnlyKnownSites() {
        HabrVacancyParser habr = new HabrVacancyParser();
        ZarplataVacancyParser zarplata = new ZarplataVacancyParser();

        assertTrue(habr.supports("https://career.habr.com/vacancies/1000169168"));
        assertFalse(habr.supports("https://zarplata.ru/vacancy/138963182"));
        assertTrue(zarplata.supports("https://zarplata.ru/vacancy/138963182"));
        assertFalse(zarplata.supports("https://career.habr.com/vacancies/1000169168"));
    }
}

# Parser - парсер вакансий Habr Career и Zarplata.ru

Spring Boot приложение, которое по ссылке на вакансию **напрямую запрашивает
страницу с сайта**, извлекает данные и сохраняет их в H2. Разбор выполняется в
кастомном пуле потоков, неудачные задачи повторяются по расписанию, а результаты
выдаются с фильтрацией, сортировкой и пагинацией.

Поддерживаемые сайты:

- `https://career.habr.com/vacancies/...`
- `https://zarplata.ru/vacancy/...`

Извлекаются: название, компания, зарплата, требования, город, дата размещения и URL.

## Быстрый запуск для проверяющего

Дополнительные настройки не нужны: база H2 работает в памяти, параметры пула и
таймаутов уже заданы в `application.properties`.

1. Откройте проект в IntelliJ IDEA (Maven-зависимости подтянутся автоматически).
2. Откройте `src/main/java/com/example/Parser/ParserApplication.java`.
3. Нажмите зелёную кнопку **Run** рядом с методом `main`.
4. Откройте `http://localhost:8080`.

Можно вообще не открывать IDE: дважды щёлкните `start.cmd` в корне проекта.
Скрипт запустит приложение и сам откроет браузер, когда сервер будет готов.

На стартовой странице есть кнопки для проверки:

- **Habr Career** и **Zarplata.ru** - разобрать реальную вакансию;
- **Асинхронно** - поставить задачу в пул и наблюдать её статус;
- **Показать /answer** - результаты из H2 с фильтрами, сортировкой и пагинацией;
- **Показать задачи** - список фоновых задач.

Альтернативный запуск из терминала:

```powershell
.\mvnw.cmd spring-boot:run
```

Linux / macOS:

```bash
./mvnw spring-boot:run
```

Требуется только **Java 21** и доступ в интернет к сайтам вакансий.

## Как парсер получает данные

- `HtmlFetchService` через `WebClient` выполняет HTTP-запрос к странице вакансии.
- HTML сразу разбирается в памяти через `jsoup` и JSON-LD.
- Сохранённые `.html`-файлы в проекте не используются: в корне проекта их нет.
- На диск попадает только журнал разборов `data/parse-history.log`.

## Соответствие критериям оценивания

| № | Критерий | Где реализовано | Как проверить                                                                     |
|---|----------|-----------------|-----------------------------------------------------------------------------------|
| 1 | Минимально рабочий сервис на Spring Boot с unit-тестами (6) | `ParserApplication`, контроллеры, сервисы; тесты `VacancyParserTest`, `VacancyServiceTest`, `ParserApplicationTests` | `.\mvnw.cmd test` - 10 тестов                                                     |
| 2 | Простые потоки (`Thread`, `Runnable`) (10) | `ParseHistoryService`: `new Thread(this::writeLoop, "parse-history-writer")`, поток запускается как daemon | строка в логе о запуске демон-потока; файл `data/parse-history.log` после разбора |
| 3 | `ExecutorService` + Scheduler, блокирующие структуры, примитивы синхронизации, собственное файловое хранилище, демон-потоки с логированием (10) | `ExecutorConfig` (`ThreadPoolExecutor`, `ArrayBlockingQueue`, `CallerRunsPolicy`, daemon `ThreadFactory`); `ParseJobService` (`@Scheduled` retry); `ParseHistoryService` (`BlockingQueue`, `ReentrantLock`, файл, `Thread`) | логи приложения, `GET /api/vacancies/jobs`, `data/parse-history.log`              |
| 4 | `ExecutorService` + Scheduler, блокирующие структуры, H2 и фильтрация через `Parallel Stream` (14) | H2 + Spring Data JPA; `VacancyService.getAnswer()` фильтрует и сортирует через `parallelStream`; `@Scheduled` в `ParseJobService` | `GET /answer?city=...&company=...&title=...&sortBy=...&direction=...`             |

## Проверка по шагам

### 1. Разбор вакансии Habr Career

На стартовой странице нажмите **Habr Career** или выполните:

```powershell
curl.exe -X POST "http://localhost:8080/api/vacancies/parse" `
  -H "Content-Type: application/json" `
  --data-binary "@parse-request.json"
```

Ожидается `200 OK` и данные, полученные с реальной страницы:

```json
{
  "title": "Системный аналитик",
  "company": "ГК Неолант",
  "salary": "от 150 000 до 250 000 ₽",
  "requirements": "Опыт работы системным аналитиком не менее 5-и лет; ...",
  "city": "Калининград, Ставрополь",
  "publicationDate": "2026-10-13",
  "url": "https://career.habr.com/vacancies/1000169168",
  "id": 1
}
```

Ссылку можно изменить на любую другую вакансию Habr Career.

### 2. Разбор вакансии Zarplata.ru

Нажмите **Zarplata.ru** или выполните:

```powershell
curl.exe -X POST "http://localhost:8080/api/vacancies/parse" `
  -H "Content-Type: application/json" `
  --data-binary "@parse-zarplata-request.json"
```

Ожидается `200 OK`:

```json
{
  "title": "Бухгалтер",
  "company": "ООО Запсибгазпром-Газификация",
  "salary": "от 60 000 ₽ за месяц на руки",
  "requirements": "Профессиональный пользователь ПК, 1С, Excel, Контур; ...",
  "city": "Тюмень",
  "publicationDate": "2026-10-29",
  "url": "https://zarplata.ru/vacancy/138963182",
  "id": 2
}
```

### 3. Результаты из H2: фильтрация, сортировка и пагинация

```powershell
curl.exe "http://localhost:8080/answer?page=0&size=10&sortBy=publicationDate&direction=desc"
```

Пример с фильтрами:

```powershell
curl.exe "http://localhost:8080/answer?city=Тюмень&title=Бухгалтер&sortBy=salary&direction=desc&page=0&size=5"
```

Параметры:

| Параметр    | По умолчанию      | Описание |
|-------------|-------------------|----------|
| `page`      | `0`               | номер страницы, начиная с 0 |
| `size`      | `10`              | размер страницы, от 1 до 100 |
| `sortBy`    | `publicationDate` | `id`, `title`, `company`, `salary`, `city`, `publicationDate` |
| `direction` | `desc`            | `asc` или `desc` |
| `city`      | —                 | вхождение подстроки без учёта регистра |
| `company`   | —                 | вхождение подстроки без учёта регистра |
| `title`     | —                 | вхождение подстроки без учёта регистра |

Фильтрация и сортировка выполняются одним конвейером
`findAll().parallelStream().filter(...).sorted(...)`; только после этого
применяется пагинация.

Пример ответа:

```json
{
  "content": [
    {
      "title": "Бухгалтер",
      "company": "ООО Запсибгазпром-Газификация",
      "salary": "от 60 000 ₽ за месяц на руки",
      "requirements": "...",
      "city": "Тюмень",
      "publicationDate": "2026-10-29",
      "url": "https://zarplata.ru/vacancy/138963182",
      "id": 2
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "sortBy": "publicationDate",
  "direction": "desc",
  "filters": {
    "city": "Тюмень"
  }
}
```

### 4. Асинхронный разбор и фоновые задачи

```powershell
curl.exe -X POST "http://localhost:8080/api/vacancies/parse/async" `
  -H "Content-Type: application/json" `
  --data-binary "@parse-request.json"
```

Ответ `202 Accepted` содержит `id` задачи. Статус:

```powershell
curl.exe "http://localhost:8080/api/vacancies/jobs/{id}"
```

Все задачи:

```powershell
curl.exe "http://localhost:8080/api/vacancies/jobs"
```

Задача выполняется в `ThreadPoolExecutor`. Если она завершилась ошибкой,
`@Scheduled`-метод `retryFailedJobs()` повторит её (до 3 попыток).

### 5. Файловая история и демон-поток

После любого разбора откройте файл `data/parse-history.log`. В нём по одной
строке на событие:

```text
2026-11-03T10:15:30.123Z | START  | https://career.habr.com/vacancies/1000169168 | jobId=..., attempt=1
2026-11-03T10:15:31.456Z | DONE   | https://career.habr.com/vacancies/1000169168 | jobId=..., id=1, title=Системный аналитик
```

Запись выполняет отдельный демон-поток `parse-history-writer`. События проходят
через ограниченную `ArrayBlockingQueue`, а доступ к файлу защищён `ReentrantLock`.

### 6. Проверка ошибки

Файл `parse-empty.json` содержит пустой URL:

```powershell
curl.exe -X POST "http://localhost:8080/api/vacancies/parse" `
  -H "Content-Type: application/json" `
  --data-binary "@parse-empty.json"
```

Ожидается `400 Bad Request` в формате `ProblemDetail`.

### 7. Консоль H2

Откройте `http://localhost:8080/h2-console`.

- JDBC URL: `jdbc:h2:mem:vacancydb`
- User Name: `sa`
- Password: пусто

Таблица: `VACANCIES`.

### 8. Unit-тесты

```powershell
.\mvnw.cmd test
```

Ожидается `BUILD SUCCESS` и 10 тестов:

- `VacancyParserTest` — разбор HTML-структур Habr и Zarplata, включая извлечение требований из блока «Требования» и из строки «Навыки/Квалификация/Специализации»;
- `VacancyServiceTest` — фильтрация, сортировка и пагинация;
- `ParserApplicationTests` — поднимается контекст Spring Boot.

## Эндпоинты

| Метод | URL | Назначение |
|-------|-----|------------|
| `GET`  | `/` | демо-страница для проверки |
| `POST` | `/api/vacancies/parse` | запросить страницу с сайта, разобрать в памяти и сохранить; ждёт результат |
| `POST` | `/api/vacancies/parse/async` | поставить задачу в пул и сразу вернуть `id` |
| `GET`  | `/api/vacancies/jobs` | список задач разбора |
| `GET`  | `/api/vacancies/jobs/{id}` | состояние задачи и результат |
| `GET`  | `/answer` | результаты из H2: фильтры, сортировка, пагинация |
| `GET`  | `/h2-console` | веб-консоль H2 |

Ошибки возвращаются в формате `ProblemDetail`:

- `400` — пустой/некорректный URL, неподдерживаемый сайт;
- `404` — задача не найдена;
- `502` — сайт недоступен или разметка не распознана;
- `504` — синхронный запрос не успел завершиться, задача продолжает работу в фоне.

## Как реализована многопоточность

- **Кастомный `ExecutorService`** - `ExecutorConfig` создаёт `ThreadPoolExecutor`
  с собственным daemon-`ThreadFactory`, ограниченной `ArrayBlockingQueue` и
  `CallerRunsPolicy`. Разборы запускаются через `CompletableFuture` в этом пуле.
- **Простые потоки** - `ParseHistoryService` создаёт обычный `Thread` с
  `Runnable` (`this::writeLoop`) и запускает его как daemon.
- **`@Scheduled`** - `ParseJobService.retryFailedJobs()` раз в минуту ищет
  неудачные задачи и повторяет их (до 3 попыток).
- **Блокирующие структуры и синхронизация** - `ArrayBlockingQueue` в пуле и в
  истории, `ReentrantLock` при записи файла, `ConcurrentHashMap` для задач,
  `AtomicInteger` / `AtomicReference` для их состояния.
- **Собственное файловое хранилище** - `data/parse-history.log`.
- **`parallelStream`** - `VacancyService.getAnswer()` фильтрует и сортирует
  вакансии из H2 параллельным потоком, затем применяет пагинацию.
- **WebClient** - HTTP-запрос к сайту; HTML обрабатывается в памяти и не
  сохраняется на диск.
- **H2 + Spring Data JPA** - хранение и выдача результатов; повторный разбор
  того же URL обновляет запись, а не создаёт дубликат.

## Структура проекта

```text
src/main/java/com/example/Parser
├── config/       — пул потоков и WebClient
├── controller/   — REST-контроллеры и обработка ошибок
├── dto/          — запросы/ответы API
├── exception/    — исключения парсера
├── job/          — состояние фоновой задачи
├── model/        — JPA-сущность Vacancy
├── parser/       — парсеры Habr и Zarplata
├── repository/   — Spring Data JPA
└── service/      — загрузка страницы, разбор, сохранение, история, выдача

src/main/resources/static/index.html — демо-страница проверки
```

## Примечания

- База `jdbc:h2:mem:vacancydb` хранится в памяти: после перезапуска данные
  очищаются. Журнал `data/parse-history.log` сохраняется между запусками.
- Если порт `8080` занят, укажите другой в `application.properties`
  (`server.port=8081`).
- Сайты могут ограничивать автоматические запросы (капча, 403). В этом случае
  используйте другую ссылку на вакансию или повторите запрос позже.
- Requirements(требования) может быть пустым, так как у вакансий разные названия требований.
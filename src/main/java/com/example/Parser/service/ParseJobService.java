package com.example.Parser.service;

import com.example.Parser.dto.ParseJobView;
import com.example.Parser.exception.ParseTimeoutException;
import com.example.Parser.exception.VacancyParseException;
import com.example.Parser.job.ParseJob;
import com.example.Parser.model.Vacancy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class ParseJobService {

    private static final Logger log = LoggerFactory.getLogger(ParseJobService.class);

    private final Map<String, ParseJob> jobs = new ConcurrentHashMap<>();
    private final VacancyParsingService parsingService;
    private final ParseHistoryService parseHistoryService;
    private final ExecutorService executorService;
    private final int maxAttempts;
    private final long timeoutSeconds;
    private final long retryDelaySeconds;

    public ParseJobService(
            VacancyParsingService parsingService,
            ParseHistoryService parseHistoryService,
            @Qualifier("vacancyExecutor") ExecutorService executorService,
            @Value("${parser.retry.max-attempts:3}") int maxAttempts,
            @Value("${parser.job.timeout-seconds:40}") long timeoutSeconds,
            @Value("${parser.retry.delay-seconds:30}") long retryDelaySeconds
    ) {
        this.parsingService = parsingService;
        this.parseHistoryService = parseHistoryService;
        this.executorService = executorService;
        this.maxAttempts = Math.max(maxAttempts, 1);
        this.timeoutSeconds = Math.max(timeoutSeconds, 5);
        this.retryDelaySeconds = Math.max(retryDelaySeconds, 1);
    }

    /**
     * Запускает задачу в пуле потоков и ждёт результат.
     * Используется синхронным POST /api/vacancies/parse.
     */
    public Vacancy submitAndWait(String url) {
        ParseJob job = createAndSubmit(url);

        try {
            return job.getCompletion().get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            throw new ParseTimeoutException(
                    "Разбор не завершился за " + timeoutSeconds
                            + " секунд. Задача продолжает выполняться в фоне, jobId=" + job.getId(),
                    job.getId()
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ParseTimeoutException(
                    "Ожидание результата прервано, jobId=" + job.getId(),
                    job.getId()
            );
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null
                    ? exception
                    : exception.getCause();
            Throwable rootCause = rootCause(cause);

            if (rootCause instanceof IllegalArgumentException illegalArgumentException) {
                throw illegalArgumentException;
            }

            if (rootCause instanceof VacancyParseException vacancyParseException) {
                throw vacancyParseException;
            }

            throw new VacancyParseException(
                    rootMessage(rootCause),
                    rootCause
            );
        }
    }

    /**
     * Асинхронный запуск: сразу возвращает jobId клиенту.
     */
    public ParseJob submit(String url) {
        return createAndSubmit(url);
    }

    public ParseJobView get(String jobId) {
        ParseJob job = jobs.get(jobId);

        if (job == null) {
            throw new NoSuchElementException("Задача не найдена: " + jobId);
        }

        return job.toView();
    }

    public List<ParseJobView> list() {
        return jobs.values()
                .stream()
                .sorted(Comparator.comparing(ParseJob::getCreatedAt).reversed())
                .map(ParseJob::toView)
                .toList();
    }

    /**
     * Планировщик периодически повторяет неудачные задачи.
     * Это демонстрирует @Scheduled вместе с кастомным пулом потоков.
     */
    @Scheduled(
            fixedDelayString = "${parser.retry.delay-ms:60000}",
            initialDelayString = "${parser.retry.initial-delay-ms:30000}"
    )
    public void retryFailedJobs() {
        Instant now = Instant.now();

        List<ParseJob> failedJobs = jobs.values()
                .stream()
                .filter(job -> job.canRetry(maxAttempts, now))
                .limit(10)
                .toList();

        if (!failedJobs.isEmpty()) {
            log.info("Планировщик повторяет {} неудачных задач", failedJobs.size());
            failedJobs.forEach(this::execute);
        }
    }

    private ParseJob createAndSubmit(String url) {
        ParseJob job = new ParseJob(UUID.randomUUID().toString(), url);
        jobs.put(job.getId(), job);
        execute(job);
        return job;
    }

    private void execute(ParseJob job) {
        if (!job.markRunning()) {
            return;
        }

        log.info("Задача {} запущена в потоке пула (попытка {})",
                job.getId(), job.getAttempts());

        parseHistoryService.record(
                job.getUrl(),
                "START",
                "jobId=" + job.getId() + ", attempt=" + job.getAttempts()
        );

        CompletableFuture.supplyAsync(
                        () -> parsingService.parseAndSave(job.getUrl()),
                        executorService
                )
                .whenComplete((vacancy, error) -> {
                    if (error != null) {
                        Instant retryAt = job.getAttempts() < maxAttempts
                                ? Instant.now().plusSeconds(retryDelaySeconds)
                                : null;

                        job.markFailed(rootMessage(error), retryAt);
                        parseHistoryService.record(
                                job.getUrl(),
                                "FAILED",
                                rootMessage(error) + (retryAt == null
                                        ? ", повтор не требуется"
                                        : ", повтор после " + retryAt)
                        );
                        log.warn("Задача {} завершилась ошибкой: {}",
                                job.getId(), rootMessage(error));
                    } else {
                        job.markDone(vacancy);
                        parseHistoryService.record(
                                job.getUrl(),
                                "DONE",
                                "jobId=" + job.getId()
                                        + ", id=" + vacancy.getId()
                                        + ", title=" + vacancy.getTitle()
                        );
                        log.info("Задача {} успешно завершена", job.getId());
                    }
                });
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = rootCause(throwable);

        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;

        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }

        return current;
    }
}

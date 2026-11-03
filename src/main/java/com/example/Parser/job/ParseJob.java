package com.example.Parser.job;

import com.example.Parser.dto.ParseJobView;
import com.example.Parser.model.Vacancy;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Задача разбора одной вакансии. Состояние меняется из разных потоков,
 * поэтому используются AtomicReference/AtomicInteger, а готовый результат
 * публикуется через CompletableFuture.
 */
public class ParseJob {

    private final String id;
    private final String url;
    private final AtomicReference<ParseJobStatus> status =
            new AtomicReference<>(ParseJobStatus.PENDING);
    private final AtomicInteger attempts = new AtomicInteger(0);
    private final CompletableFuture<Vacancy> completion = new CompletableFuture<>();
    private final Instant createdAt = Instant.now();

    private volatile Vacancy result;
    private volatile String error;
    private volatile Instant updatedAt = createdAt;
    private volatile Instant nextRetryAt;

    public ParseJob(String id, String url) {
        this.id = id;
        this.url = url;
    }

    public String getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public ParseJobStatus getStatus() {
        return status.get();
    }

    public int getAttempts() {
        return attempts.get();
    }

    public Vacancy getResult() {
        return result;
    }

    public String getError() {
        return error;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public CompletableFuture<Vacancy> getCompletion() {
        return completion;
    }

    public boolean canRetry(int maxAttempts, Instant now) {
        return getStatus() == ParseJobStatus.FAILED
                && getAttempts() < maxAttempts
                && nextRetryAt != null
                && !nextRetryAt.isAfter(now);
    }

    public boolean markRunning() {
        boolean started = status.compareAndSet(ParseJobStatus.PENDING, ParseJobStatus.RUNNING)
                || status.compareAndSet(ParseJobStatus.FAILED, ParseJobStatus.RUNNING);

        if (started) {
            attempts.incrementAndGet();
            error = null;
            nextRetryAt = null;
            updatedAt = Instant.now();
        }

        return started;
    }

    public void markDone(Vacancy vacancy) {
        result = vacancy;
        error = null;
        nextRetryAt = null;
        updatedAt = Instant.now();
        status.set(ParseJobStatus.DONE);
        completion.complete(vacancy);
    }

    public void markFailed(String failureMessage, Instant retryAt) {
        result = null;
        error = failureMessage;
        nextRetryAt = retryAt;
        updatedAt = Instant.now();
        status.set(ParseJobStatus.FAILED);
        completion.completeExceptionally(new IllegalStateException(failureMessage));
    }

    public ParseJobView toView() {
        return new ParseJobView(
                id,
                url,
                status.get().name(),
                attempts.get(),
                error,
                result,
                createdAt,
                updatedAt,
                nextRetryAt
        );
    }
}

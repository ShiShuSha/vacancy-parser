package com.example.Parser.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Собственное файловое хранилище истории разборов.
 *
 * <p>События складываются в ограниченную блокирующую очередь, а отдельный
 * демон-поток (обычный {@link Thread} с {@link Runnable}) по одному записывает
 * их в файл. Доступ к файлу защищён {@link ReentrantLock}, поэтому запись
 * безопасна при обращении из нескольких потоков пула.</p>
 */
@Service
public class ParseHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ParseHistoryService.class);

    private final BlockingQueue<String> events;
    private final ReentrantLock fileLock = new ReentrantLock();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Path historyFile;
    private final Thread writerThread;

    public ParseHistoryService(
            @Value("${parser.history.file:data/parse-history.log}") String historyFile,
            @Value("${parser.history.queue-capacity:200}") int queueCapacity
    ) {
        this.historyFile = Path.of(historyFile).toAbsolutePath().normalize();
        this.events = new ArrayBlockingQueue<>(Math.max(queueCapacity, 1));

        this.writerThread = new Thread(this::writeLoop, "parse-history-writer");
        this.writerThread.setDaemon(true);
        this.writerThread.start();

        log.info("Запущен демон-поток {} (daemon=true), файл истории: {}",
                writerThread.getName(), this.historyFile);
    }

    /**
     * Добавляет событие в очередь. Метод не блокирует поток разбора:
     * если очередь переполнена, событие пропускается с предупреждением.
     */
    public void record(String url, String status, String details) {
        String event = "%s | %-6s | %s | %s".formatted(
                Instant.now(),
                status,
                url == null ? "" : url,
                oneLine(details)
        );

        if (!events.offer(event)) {
            log.warn("Очередь истории переполнена, событие пропущено: {}", event);
        }
    }

    public Path getHistoryFile() {
        return historyFile;
    }

    private void writeLoop() {
        while (running.get()) {
            try {
                String event = events.poll(1, TimeUnit.SECONDS);

                if (event != null) {
                    appendToFile(event);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void appendToFile(String event) {
        fileLock.lock();

        try {
            Path parent = historyFile.getParent();

            if (parent != null) {
                Files.createDirectories(parent);
            }

            Files.writeString(
                    historyFile,
                    event + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException exception) {
            log.error("Не удалось записать историю разбора в {}", historyFile, exception);
        } finally {
            fileLock.unlock();
        }
    }

    private String oneLine(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = value
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        return normalized.length() <= 500
                ? normalized
                : normalized.substring(0, 500);
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        writerThread.interrupt();

        try {
            writerThread.join(2_000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }

        List<String> remaining = new ArrayList<>();
        events.drainTo(remaining);
        remaining.forEach(this::appendToFile);

        log.info("Демон-поток {} остановлен, записей при остановке: {}",
                writerThread.getName(), remaining.size());
    }
}

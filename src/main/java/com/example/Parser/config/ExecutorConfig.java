package com.example.Parser.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Пул потоков для параллельной загрузки страниц и разбора вакансий.
 * Очередь ограничена, чтобы при большом количестве запросов не закончилась память.
 * Рабочие потоки — демоны с логированием необработанных ошибок.
 */
@Configuration
public class ExecutorConfig {

    private static final Logger log = LoggerFactory.getLogger(ExecutorConfig.class);

    @Bean(name = "vacancyExecutor", destroyMethod = "shutdown")
    public ExecutorService vacancyExecutor(
            @Value("${parser.executor.core-size:4}") int coreSize,
            @Value("${parser.executor.max-size:8}") int maxSize,
            @Value("${parser.executor.queue-capacity:100}") int queueCapacity
    ) {
        int safeCoreSize = Math.max(coreSize, 1);
        int safeMaxSize = Math.max(maxSize, safeCoreSize);
        int safeQueueCapacity = Math.max(queueCapacity, 1);

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                safeCoreSize,
                safeMaxSize,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(safeQueueCapacity),
                new ParserThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(true);

        log.info("Создан пул потоков: core={}, max={}, queue={}",
                safeCoreSize, safeMaxSize, safeQueueCapacity);

        return executor;
    }

    private static final class ParserThreadFactory implements ThreadFactory {

        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "vacancy-parser-" + counter.getAndIncrement());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((failedThread, error) ->
                    log.error("Необработанная ошибка в потоке {}",
                            failedThread.getName(), error));
            log.debug("Создан демон-поток пула: {}", thread.getName());
            return thread;
        }
    }
}

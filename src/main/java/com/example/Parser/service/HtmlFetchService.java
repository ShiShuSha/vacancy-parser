package com.example.Parser.service;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Service
public class HtmlFetchService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(25);
    private static final int MAX_HTML_SIZE = 5 * 1024 * 1024;

    private final WebClient webClient;

    public HtmlFetchService(WebClient.Builder webClientBuilder) {
        HttpClient httpClient = HttpClient.create()
                .followRedirect(true)
                .responseTimeout(REQUEST_TIMEOUT);

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(MAX_HTML_SIZE))
                .build();

        this.webClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .defaultHeader(
                        HttpHeaders.USER_AGENT,
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                                + "AppleWebKit/537.36 (KHTML, like Gecko) "
                                + "Chrome/130.0.0.0 Safari/537.36"
                )
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, "ru-RU,ru;q=0.9,en;q=0.8")
                .build();
    }

    /**
     * Асинхронно получает HTML страницы вакансии в память.
     */
    public Mono<String> fetchHtml(String url) {
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(REQUEST_TIMEOUT);
    }

    /**
     * Блокирующий вариант нужен внутри рабочего потока пула: сам запрос
     * по-прежнему выполняется через WebClient.
     */
    public String fetchHtmlBlocking(String url) {
        return fetchHtml(url)
                .block(Duration.ofSeconds(30));
    }
}

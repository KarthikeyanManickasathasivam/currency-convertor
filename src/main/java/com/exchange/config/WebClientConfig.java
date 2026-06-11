package com.exchange.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class WebClientConfig {

    @Value("${external.api.exchangerate-host.base-url}")
    private String exchangeRateHostBaseUrl;

    @Value("${external.api.alpha-vantage.base-url}")
    private String alphaVantageBaseUrl;

    @Bean("exchangeRateHostWebClient")
    public WebClient exchangeRateHostWebClient() {
        return buildClient(exchangeRateHostBaseUrl, 5000);
    }

    @Bean("alphaVantageWebClient")
    public WebClient alphaVantageWebClient() {
        return buildClient(alphaVantageBaseUrl, 5000);
    }

    private WebClient buildClient(String baseUrl, int timeoutMs) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutMs)
                .responseTimeout(Duration.ofMillis(timeoutMs))
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS)));
        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}

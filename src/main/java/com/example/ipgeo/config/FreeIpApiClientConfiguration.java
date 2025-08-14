package com.example.ipgeo.config;

import com.example.ipgeo.config.AppProperties;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration(proxyBeanMethods = false)
public class FreeIpApiClientConfiguration {

    @Bean(name = "freeIpApiWebClient")
    public WebClient freeIpApiWebClient(WebClient.Builder builder, AppProperties props) {
        AppProperties.Freeipapi cfg = props.getFreeipapi();
        String baseUrl = cfg.getBaseUrl();
        Duration readWriteTimeout = cfg.getTimeout();
        int connectTimeoutMs = Math.toIntExact(cfg.getConnectTimeout().toMillis());

        HttpClient http = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(readWriteTimeout)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(readWriteTimeout.toMillis(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(readWriteTimeout.toMillis(), TimeUnit.MILLISECONDS)));

        return builder
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(http))
                .build();
    }
}

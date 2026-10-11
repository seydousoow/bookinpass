package com.bookinpass.backend.config;

import com.bookinpass.backend.client.DuffelClient;
import com.bookinpass.backend.client.ExpediaClient;
import com.bookinpass.backend.client.FixtureDuffelClient;
import com.bookinpass.backend.client.HttpDuffelClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Date;

@Slf4j
@Configuration(proxyBeanMethods = false)
public class HttpClientConfig {

    /**
     * Le choix démo / réel se fait ici, à l'exécution. Une condition Spring ({@code @ConditionalOnProperty})
     * serait figée au moment de la compilation native : l'image ne pourrait plus changer de mode.
     */
    @Bean
    DuffelClient duffelClient(DuffelProperties properties, RestClient.Builder builder, ResourceLoader resources) {
        if (properties.demoMode()) {
            log.warn("DUFFEL_ACCESS_TOKEN is not set: serving recorded demo responses from {}", properties.fixturesDir());
            return new FixtureDuffelClient(resources, properties.fixturesDir());
        }
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(properties.readTimeout());
        RestClient client = builder
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken())
                .defaultHeader("Duffel-Version", properties.apiVersion())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        return new HttpDuffelClient(client, properties);
    }

    @Bean
    ExpediaClient expediaClient(ExpediaProperties properties, RestClient.Builder builder) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(properties.readTimeout());
        RestClient client = builder
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings))
                .defaultHeader(HttpHeaders.AUTHORIZATION, generateSignature(properties.apiKey(), properties.secret()))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        return new ExpediaClient(client);
    }

    private String generateSignature(String apiKey, String secret) {
        var timestamp = (new Date().getTime() / 1000);
        var toBeHashed = apiKey + secret + timestamp;
        try {
            var md = MessageDigest.getInstance("SHA-512");
            var bytes = md.digest(toBeHashed.getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder();
            for (var aByte : bytes) {
                sb.append(Integer.toString((aByte & 0xff) + 0x100, 16).substring(1));
            }
            var signature = sb.toString();
            return "EAN apikey=" + apiKey + ",signature=" + signature + ",timestamp=" + timestamp;
        }  catch (NoSuchAlgorithmException e) {
            log.error("Invalid algorithm SHA-512: {}", e.getMessage(), e);
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }
}

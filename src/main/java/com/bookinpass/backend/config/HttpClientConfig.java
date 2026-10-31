package com.bookinpass.backend.config;

import com.bookinpass.backend.client.ClaudeClient;
import com.bookinpass.backend.client.DuffelClient;
import com.bookinpass.backend.client.ExpediaClient;
import com.bookinpass.backend.client.FixtureDuffelClient;
import com.bookinpass.backend.client.FixtureStaysClient;
import com.bookinpass.backend.client.HttpDuffelClient;
import com.bookinpass.backend.client.HttpStaysClient;
import com.bookinpass.backend.client.StaysClient;
import com.bookinpass.backend.client.PayDunyaClient;
import com.bookinpass.backend.client.StripeClient;
import com.bookinpass.backend.dto.duffel.DuffelCustomerUser;
import com.bookinpass.backend.dto.duffel.DuffelOfferRequest;
import com.bookinpass.backend.dto.duffel.DuffelOrderRequest;
import com.bookinpass.backend.dto.duffel.DuffelOrderUpdate;
import com.bookinpass.backend.dto.duffel.DuffelPassengerUpdate;
import com.bookinpass.backend.dto.duffel.DuffelPriceRequest;
import com.bookinpass.backend.dto.duffel.DuffelQuoteRequest;
import com.bookinpass.backend.dto.duffel.DuffelStaySearch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Date;
import java.util.zip.GZIPInputStream;

@Slf4j
@Configuration(proxyBeanMethods = false)
// Image native : corps envoyés à Duffel, sérialisés par Jackson via réflexion.
@RegisterReflectionForBinding({DuffelOfferRequest.class, DuffelPassengerUpdate.class, DuffelPriceRequest.class,
        DuffelOrderRequest.class, DuffelOrderUpdate.class, DuffelCustomerUser.class, DuffelStaySearch.class, DuffelQuoteRequest.class})
public class HttpClientConfig {

    /**
     * Le choix démo / réel se fait ici, à l'exécution. Une condition Spring ({@code @ConditionalOnProperty})
     * serait figée au moment de la compilation native : l'image ne pourrait plus changer de mode.
     */
    @Bean
    DuffelClient duffelClient(DuffelProperties properties, RestClient.Builder builder, ResourceLoader resources, JsonMapper json) {
        if (properties.demoMode()) {
            log.warn("DUFFEL_ACCESS_TOKEN is not set: serving recorded demo responses from {}", properties.fixturesDir());
            return new FixtureDuffelClient(resources, properties.fixturesDir(), json);
        }
        return new HttpDuffelClient(duffelRestClient(properties, builder, DuffelClient.class), properties, json);
    }

    /**
     * Même règle pour les hébergements, avec une condition de plus : Duffel Stays doit être activé sur le compte.
     */
    @Bean
    StaysClient staysClient(DuffelProperties properties, RestClient.Builder builder, ResourceLoader resources, JsonMapper json) {
        if (properties.staysDemoMode()) {
            log.warn("Duffel Stays is not enabled (DUFFEL_STAYS_ENABLED): serving demo accommodation from {}", properties.fixturesDir());
            return new FixtureStaysClient(resources, properties.fixturesDir(), json);
        }
        return new HttpStaysClient(duffelRestClient(properties, builder, StaysClient.class), json);
    }

    /**
     * Client HTTP Duffel : jeton, version d'API, JSON, compression et journal (sous le logger {@code logAs}).
     * {@code clone()} : le constructeur injecté est partagé, chaque client garde sa configuration.
     */
    private static RestClient duffelRestClient(DuffelProperties properties, RestClient.Builder builder, Class<?> logAs) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(properties.readTimeout());
        return builder.clone()
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken())
                .defaultHeader("Duffel-Version", properties.apiVersion())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                // Ordre : le journal enveloppe la décompression, il voit donc le JSON en clair.
                .requestInterceptor(logging(logAs))
                .requestInterceptor(GzipResponse.INTERCEPTOR)
                .build();
    }

    /** Stripe : clé secrète en en-tête ; sans journal des corps (ils contiennent le `client_secret`). */
    @Bean
    StripeClient stripeClient(StripeProperties properties, RestClient.Builder builder, JsonMapper json) {
        if (!properties.enabled()) {
            log.warn("STRIPE_SECRET_KEY / STRIPE_PUBLISHABLE_KEY are not set: payments are unavailable");
        }
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(Duration.ofSeconds(30));
        RestClient client = builder.clone()
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.secretKey())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Stripe-Version", properties.apiVersion())
                .build();
        return new StripeClient(client, json);
    }

    /** PayDunya : trois clés en en-têtes ; corps non journalisés (coordonnées des clients). */
    @Bean
    PayDunyaClient payDunyaClient(PayDunyaProperties properties, RestClient.Builder builder, JsonMapper json) {
        if (!properties.enabled()) {
            log.info("PAYDUNYA_* keys are not set: Wave / Orange Money are hidden");
        }
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(Duration.ofSeconds(30));
        RestClient client = builder.clone()
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("PAYDUNYA-MASTER-KEY", nullToEmpty(properties.masterKey()))
                .defaultHeader("PAYDUNYA-PRIVATE-KEY", nullToEmpty(properties.privateKey()))
                .defaultHeader("PAYDUNYA-TOKEN", nullToEmpty(properties.token()))
                .build();
        return new PayDunyaClient(client, json);
    }

    /** Claude : clé en en-tête, version d'API figée ; corps non journalisés (textes des voyageurs). */
    @Bean
    ClaudeClient claudeClient(AnthropicProperties properties, RestClient.Builder builder, JsonMapper json) {
        if (!properties.enabled()) {
            log.info("ANTHROPIC_API_KEY is not set: reviews wait for a manager, no review summaries");
        }
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(properties.readTimeout());
        RestClient client = builder.clone()
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings))
                .defaultHeader("x-api-key", nullToEmpty(properties.apiKey()))
                .defaultHeader("anthropic-version", properties.apiVersion())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        return new ClaudeClient(client, json, properties.model(), properties.fallbacks(), properties.thinking());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
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
                .requestInterceptor(logging(ExpediaClient.class))
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

    /**
     * Journal des appels sortants, sous le logger du client (ex. {@code com.bookinpass.backend.client.DuffelClient}) :
     * <ul>
     *     <li>DEBUG — méthode, URL, statut et durée ;</li>
     *     <li>TRACE — en plus, en-têtes et corps de la requête et de la réponse.</li>
     * </ul>
     * Les identifiants ({@code Authorization}) n'apparaissent jamais. Au niveau TRACE la réponse est lue en mémoire
     * pour être journalisée (une recherche Duffel pèse jusqu'à ~17 Mo) : à réserver au diagnostic.
     */
    private static ClientHttpRequestInterceptor logging(Class<?> client) {
        Logger logger = LoggerFactory.getLogger(client);
        return (request, body, execution) -> {
            if (!logger.isDebugEnabled()) {
                return execution.execute(request, body);
            }
            if (logger.isTraceEnabled()) {
                logger.trace("→ {} {} headers={} body={}", request.getMethod(), request.getURI(), masked(request.getHeaders()), new String(body, StandardCharsets.UTF_8));
            }
            long start = System.nanoTime();
            ClientHttpResponse response;
            try {
                response = execution.execute(request, body);
            } catch (IOException e) {
                logger.debug("{} {} failed after {} ms: {}", request.getMethod(), request.getURI(), elapsedMillis(start), e.toString());
                throw e;
            }
            logger.debug("{} {} → {} ({} ms)", request.getMethod(), request.getURI(), response.getStatusCode().value(), elapsedMillis(start));
            if (!logger.isTraceEnabled()) {
                return response;
            }
            BufferedResponse buffered = new BufferedResponse(response);
            logger.trace("← {} {} headers={} body={}", response.getStatusCode().value(), request.getURI(), masked(response.getHeaders()), new String(buffered.content, StandardCharsets.UTF_8));
            return buffered;
        };
    }

    private static String masked(HttpHeaders headers) {
        HttpHeaders copy = new HttpHeaders();
        copy.putAll(headers);
        if (copy.containsHeader(HttpHeaders.AUTHORIZATION)) {
            copy.set(HttpHeaders.AUTHORIZATION, "***");
        }
        return copy.toString();
    }

    private static long elapsedMillis(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    /**
     * Compression des réponses, recommandée par Duffel : les recherches pèsent jusqu’à ~17 Mo en clair.
     * Le client HTTP du JDK ne décompresse pas seul : on demande gzip, puis on décompresse ici à la lecture.
     */
    @NullMarked
    private static final class GzipResponse implements ClientHttpResponse {

        static final ClientHttpRequestInterceptor INTERCEPTOR = (request, body, execution) -> {
            request.getHeaders().set(HttpHeaders.ACCEPT_ENCODING, "gzip");
            ClientHttpResponse response = execution.execute(request, body);
            return "gzip".equalsIgnoreCase(response.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING))
                    ? new GzipResponse(response)
                    : response;
        };

        private final ClientHttpResponse delegate;
        private final HttpHeaders headers;

        private GzipResponse(ClientHttpResponse delegate) {
            this.delegate = delegate;
            // Le corps rendu n'est plus compressé : ces en-têtes décriraient l'ancien.
            this.headers = new HttpHeaders();
            this.headers.putAll(delegate.getHeaders());
            this.headers.remove(HttpHeaders.CONTENT_ENCODING);
            this.headers.remove(HttpHeaders.CONTENT_LENGTH);
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return headers;
        }

        @Override
        public InputStream getBody() throws IOException {
            return new GZIPInputStream(delegate.getBody());
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    /**
     * Réponse lue une fois pour le journal, puis rejouée telle quelle au {@code RestClient}.
     */
    @NullMarked
    private static final class BufferedResponse implements ClientHttpResponse {

        private final ClientHttpResponse delegate;
        private final byte[] content;

        private BufferedResponse(ClientHttpResponse delegate) throws IOException {
            this.delegate = delegate;
            try (InputStream in = delegate.getBody()) {
                this.content = in.readAllBytes();
            }
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}

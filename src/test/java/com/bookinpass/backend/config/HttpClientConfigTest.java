package com.bookinpass.backend.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.bookinpass.backend.client.DuffelClient;
import com.bookinpass.backend.client.FixtureDuffelClient;
import com.bookinpass.backend.client.FixtureStaysClient;
import com.bookinpass.backend.client.HttpDuffelClient;
import com.bookinpass.backend.client.HttpStaysClient;
import com.bookinpass.backend.client.StaysClient;
import com.bookinpass.backend.exception.DuffelException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Clients construits comme en production, contre un vrai serveur HTTP local : en-têtes, décompression, journal masqué.
 */
class HttpClientConfigTest {

    private static final String BODY = "{\"data\":{\"id\":\"off_1\"}}";

    private final HttpClientConfig config = new HttpClientConfig();
    private final JsonMapper json = JsonMapper.builder().build();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> version = new AtomicReference<>();
    private final AtomicReference<String> acceptEncoding = new AtomicReference<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(DuffelClient.class);
    private HttpServer server;

    private static byte[] gzip() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream zip = new GZIPOutputStream(out)) {
            zip.write(HttpClientConfigTest.BODY.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    @BeforeEach
    void localServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/air/offers/off_gzip", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            version.set(exchange.getRequestHeaders().getFirst("Duffel-Version"));
            acceptEncoding.set(exchange.getRequestHeaders().getFirst("Accept-Encoding"));
            byte[] compressed = gzip();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            exchange.sendResponseHeaders(200, compressed.length);
            exchange.getResponseBody().write(compressed);
            exchange.close();
        });
        server.createContext("/air/offers/off_plain", exchange -> {
            byte[] plain = BODY.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, plain.length);
            exchange.getResponseBody().write(plain);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void cleanUp() {
        server.stop(0);
        logger.setLevel(null);
    }

    private String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private DuffelProperties duffel(String token, boolean stays) {
        return new DuffelProperties(url(), token, "v2", Duration.ofSeconds(10), Duration.ofSeconds(5), "file:fixtures/duffel", stays, 10, "");
    }

    private DuffelClient live() {
        return config.duffelClient(duffel("duffel_test_secret", true), RestClient.builder(), new DefaultResourceLoader(), json);
    }

    @Test
    void withoutATokenDuffelIsServedFromTheFixtures() {
        assertThat(config.duffelClient(duffel("", true), RestClient.builder(), new DefaultResourceLoader(), json)).isInstanceOf(FixtureDuffelClient.class);
        assertThat(config.duffelClient(duffel(null, true), RestClient.builder(), new DefaultResourceLoader(), json)).isInstanceOf(FixtureDuffelClient.class);
    }

    @Test
    void staysNeedATokenAndTheirFlag() {
        assertThat(config.staysClient(duffel("token", false), RestClient.builder(), new DefaultResourceLoader(), json)).isInstanceOf(FixtureStaysClient.class);
        StaysClient stays = config.staysClient(duffel("token", true), RestClient.builder(), new DefaultResourceLoader(), json);
        assertThat(stays).isInstanceOf(HttpStaysClient.class);
    }

    @Test
    void liveClientSendsItsHeadersAndUnzipsTheResponse() {
        DuffelClient client = live();
        assertThat(client).isInstanceOf(HttpDuffelClient.class);

        assertThat(client.getOffer("off_gzip")).isEqualTo(BODY);

        assertThat(authorization.get()).isEqualTo("Bearer duffel_test_secret");
        assertThat(version.get()).isEqualTo("v2");
        assertThat(acceptEncoding.get()).isEqualTo("gzip");
    }

    @Test
    void anUncompressedResponseIsReadAsIs() {
        assertThat(live().getOffer("off_plain")).isEqualTo(BODY);
    }

    @Test
    void debugLoggingLeavesTheResponseIntact() {
        logger.setLevel(Level.DEBUG);

        assertThat(live().getOffer("off_gzip")).isEqualTo(BODY);
    }

    @Test
    void traceLoggingBuffersTheBodyAndMasksTheToken() {
        logger.setLevel(Level.TRACE);
        List<String> lines = new java.util.concurrent.CopyOnWriteArrayList<>();
        ch.qos.logback.core.AppenderBase<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ch.qos.logback.core.AppenderBase<>() {
            @Override
            protected void append(ch.qos.logback.classic.spi.ILoggingEvent event) {
                lines.add(event.getFormattedMessage());
            }
        };
        appender.start();
        logger.addAppender(appender);
        try {
            assertThat(live().getOffer("off_gzip")).isEqualTo(BODY);
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(String.join("\n", lines)).contains(BODY).contains("***").doesNotContain("duffel_test_secret");
    }

    @Test
    void aNetworkFailureIsLoggedThenRethrown() {
        logger.setLevel(Level.DEBUG);
        DuffelClient client = live();
        server.stop(0);

        assertThatThrownBy(() -> client.getOffer("off_gzip")).isInstanceOf(ResourceAccessException.class);
    }

    @Test
    void duffelErrorsStayErrorsThroughTheInterceptors() {
        assertThatThrownBy(() -> live().getOffer("off_missing")).isInstanceOf(DuffelException.class);
    }

    @Test
    void paymentAndReviewClientsAreBuiltEvenWithoutKeys() {
        assertThat(config.stripeClient(new StripeProperties(url(), "", "", "2026-03-25", "", "http://localhost"), RestClient.builder(), json)).isNotNull();
        assertThat(config.stripeClient(new StripeProperties(url(), "sk", "pk", "2026-03-25", "", "http://localhost"), RestClient.builder(), json)).isNotNull();
        assertThat(config.payDunyaClient(new PayDunyaProperties(url(), null, null, null, List.of(), null, "Store"), RestClient.builder(), json)).isNotNull();
        assertThat(config.payDunyaClient(new PayDunyaProperties(url(), "m", "p", "t", List.of(), "http://cb", "Store"), RestClient.builder(), json)).isNotNull();
        assertThat(config.claudeClient(new AnthropicProperties(url(), null, "m", "v", false, false, Duration.ofSeconds(5)), RestClient.builder(), json)).isNotNull();
        assertThat(config.claudeClient(new AnthropicProperties(url(), "k", "m", "v", true, true, Duration.ofSeconds(5)), RestClient.builder(), json)).isNotNull();
        assertThat(config.expediaClient(new ExpediaProperties(url(), "key", "secret", Duration.ofSeconds(5)), RestClient.builder())).isNotNull();
    }
}

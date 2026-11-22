package ch.omidahmadi.vpscontroller.check;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

class TelegramNotifierTest {

    private static final String TOKEN = "111:super-secret-token";

    private HttpServer server;
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private final AtomicInteger status = new AtomicInteger(200);

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsTheGermanDownMessageToTheBotEndpoint() {
        TelegramNotifier notifier = notifier();

        notifier.wentDown("Shop", "HTTP 525 (erwartet 200-399)", Instant.parse("2026-11-23T09:41:00Z"));

        assertThat(paths).singleElement().isEqualTo("/bot" + TOKEN + "/sendMessage");
        assertThat(bodies).singleElement().satisfies(body -> assertThat(body)
                .contains("\"chat_id\":\"42\"")
                .contains("Ausgefallen: Shop – HTTP 525 (erwartet 200-399) (seit 09:41)"));
    }

    @Test
    void sendsTheGermanRecoveryMessage() {
        TelegramNotifier notifier = notifier();

        notifier.recovered("Shop", Duration.ofMinutes(12));

        assertThat(bodies).singleElement()
                .satisfies(body -> assertThat(body).contains("Wieder erreichbar: Shop nach 12 min"));
    }

    @Test
    void aFailingSendIsRetriedOnceAndNeverThrows() {
        status.set(500);
        TelegramNotifier notifier = notifier();

        notifier.wentDown("Shop", "HTTP 500 (erwartet 200-399)", Instant.now());

        assertThat(bodies).hasSize(2);
    }

    @Test
    void tokenNeverReachesTheLog() {
        status.set(500);
        ListAppender<ILoggingEvent> logs = attachAppender();

        notifier().wentDown("Shop", "HTTP 500 (erwartet 200-399)", Instant.now());

        assertThat(logs.list).isNotEmpty();
        assertThat(logs.list).allSatisfy(event ->
                assertThat(event.getFormattedMessage()).doesNotContain(TOKEN).doesNotContain("secret"));
    }

    private TelegramNotifier notifier() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                paths.add(exchange.getRequestURI().getPath());
                bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(status.get(), -1);
                exchange.close();
            });
            server.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        RestClient rest = RestClient.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build();
        return new TelegramNotifier(rest, TOKEN, "42", ZoneOffset.UTC);
    }

    private ListAppender<ILoggingEvent> attachAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(TelegramNotifier.class);
        logger.setLevel(Level.WARN);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }
}

package ch.omidahmadi.vpscontroller.check;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Wires the check machinery: a shared clock, the probe, the state machine, heartbeat and the chosen notifier. */
@Configuration
class CheckBeansConfig {

    private static final Logger log = LoggerFactory.getLogger(CheckBeansConfig.class);

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    HttpProbe httpProbe() {
        return new HttpProbe();
    }

    @Bean
    CheckStateMachine checkStateMachine() {
        return new CheckStateMachine();
    }

    @Bean
    Heartbeat heartbeat(@Value("${vps.heartbeat.url:}") String url, Clock clock) {
        URI uri = url.isBlank() ? null : URI.create(url);
        return new Heartbeat(restClient(null), uri, clock);
    }

    @Bean
    Notifier notifier(@Value("${TELEGRAM_BOT_TOKEN:}") String token,
                      @Value("${TELEGRAM_CHAT_ID:}") String chatId,
                      @Value("${vps.telegram.base-url:https://api.telegram.org}") String baseUrl, Clock clock) {
        if (!token.isBlank() && !chatId.isBlank()) {
            log.info("Telegram-Benachrichtigungen aktiv");
            return new TelegramNotifier(restClient(baseUrl), token, chatId, clock.getZone());
        }
        log.info("Benachrichtigungen nur ins Log (kein TELEGRAM_BOT_TOKEN/TELEGRAM_CHAT_ID gesetzt)");
        return new LogNotifier();
    }

    private static RestClient restClient(String baseUrl) {
        Duration timeout = Duration.ofSeconds(10);
        HttpClient http = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(timeout);
        RestClient.Builder builder = RestClient.builder().requestFactory(factory);
        return (baseUrl == null ? builder : builder.baseUrl(baseUrl)).build();
    }
}

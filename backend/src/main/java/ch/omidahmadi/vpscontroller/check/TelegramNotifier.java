package ch.omidahmadi.vpscontroller.check;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Sends alerts through the Telegram Bot API. A failure is logged and retried once; the token is never logged
 * (exception messages would carry it in the request URL, so only a generic text is logged).
 */
class TelegramNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotifier.class);

    private final RestClient rest;
    private final String token;
    private final String chatId;
    private final ZoneId zone;

    TelegramNotifier(RestClient rest, String token, String chatId, ZoneId zone) {
        this.rest = rest;
        this.token = token;
        this.chatId = chatId;
        this.zone = zone;
    }

    @Override
    public void wentDown(String name, String error, Instant at) {
        send(CheckMessages.wentDown(name, error, at, zone));
    }

    @Override
    public void recovered(String name, Duration outage) {
        send(CheckMessages.recovered(name, outage));
    }

    private void send(String text) {
        try {
            post(text);
        } catch (RestClientException first) {
            log.warn("Telegram-Versand fehlgeschlagen, ein Wiederholungsversuch");
            try {
                post(text);
            } catch (RestClientException second) {
                log.warn("Telegram-Versand endgültig fehlgeschlagen");
            }
        }
    }

    private void post(String text) {
        rest.post()
                .uri("/bot{token}/sendMessage", token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("chat_id", chatId, "text", text))
                .retrieve()
                .toBodilessEntity();
    }
}

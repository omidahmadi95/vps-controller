package ch.omidahmadi.vpscontroller.check;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import javax.net.ssl.SSLException;

/**
 * One HTTP availability probe with the JDK {@link HttpClient}. Follows normal redirects and applies the
 * configured timeout to both connect and request. Classification of a non-2xx answer is left to
 * {@link StatusRanges}; everything else becomes a short German error text.
 */
public class HttpProbe {

    public ProbeResult check(String url, Duration timeout, StatusRanges accept) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build();

        long start = System.nanoTime();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            long ms = (System.nanoTime() - start) / 1_000_000;
            int code = response.statusCode();
            boolean ok = accept.accepts(code);
            return ProbeResult.of(code, ms, ok, ok ? null : "HTTP " + code + " (erwartet " + accept.describe() + ")");
        } catch (HttpTimeoutException e) {
            return ProbeResult.failed("Zeitüberschreitung nach " + humanTimeout(timeout));
        } catch (IOException e) {
            if (rootCause(e) instanceof SSLException) {
                return ProbeResult.failed("TLS-Fehler: " + shortCause(e));
            }
            return ProbeResult.failed("Keine Verbindung: " + shortCause(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ProbeResult.failed("Abgebrochen");
        }
    }

    static String humanTimeout(Duration timeout) {
        return timeout.toSeconds() > 0 ? timeout.toSeconds() + " s" : timeout.toMillis() + " ms";
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static String shortCause(Throwable t) {
        Throwable cause = rootCause(t);
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}

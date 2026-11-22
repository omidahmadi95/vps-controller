package ch.omidahmadi.vpscontroller.container;

import java.util.Arrays;
import java.util.List;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Read-only access to a Docker Engine API (in production: docker-socket-proxy, GET only). */
public class DockerClient {

    private final RestClient rest;

    public DockerClient(RestClient rest) {
        this.rest = rest;
    }

    public List<DockerContainer> listContainers() {
        try {
            DockerContainer[] body = rest.get().uri("/containers/json?all=true").retrieve().body(DockerContainer[].class);
            if (body == null) {
                throw new SourceUnreachableException("Docker-Quelle hat eine leere Antwort geliefert", null);
            }
            return Arrays.asList(body);
        } catch (RestClientException e) {
            // Covers connection refused, timeouts, HTTP 4xx/5xx and unreadable JSON.
            throw new SourceUnreachableException("Docker-Quelle nicht erreichbar: " + e.getMessage(), e);
        }
    }
}

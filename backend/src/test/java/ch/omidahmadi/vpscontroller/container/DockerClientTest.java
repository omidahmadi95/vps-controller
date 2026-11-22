package ch.omidahmadi.vpscontroller.container;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DockerClientTest {

    private MockRestServiceServer server;
    private DockerClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://docker");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new DockerClient(builder.build());
    }

    @Test
    void readsContainersAndIgnoresUnknownFields() {
        server.expect(requestTo("http://docker/containers/json?all=true")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{"Id":"a1","Names":["/caddy"],"Image":"caddy:latest","State":"running",
                          "Status":"Up 4 days (healthy)","Ports":[],"Labels":{}}]
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.listContainers())
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.names()).containsExactly("/caddy");
                    assertThat(c.state()).isEqualTo("running");
                });
        server.verify();
    }

    @Test
    void serverErrorMeansUnreachable() {
        server.expect(requestTo("http://docker/containers/json?all=true")).andRespond(withServerError());
        assertThatThrownBy(client::listContainers).isInstanceOf(SourceUnreachableException.class);
    }

    @Test
    void connectionFailureMeansUnreachable() {
        server.expect(requestTo("http://docker/containers/json?all=true")).andRespond(withException(new IOException("Connection refused")));
        assertThatThrownBy(client::listContainers).isInstanceOf(SourceUnreachableException.class);
    }

    @Test
    void brokenJsonMeansUnreachable() {
        server.expect(requestTo("http://docker/containers/json?all=true"))
                .andRespond(withSuccess("<html>proxy error</html>", MediaType.APPLICATION_JSON));
        assertThatThrownBy(client::listContainers).isInstanceOf(SourceUnreachableException.class);
    }
}

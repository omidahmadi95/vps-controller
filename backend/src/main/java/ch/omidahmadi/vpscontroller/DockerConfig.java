package ch.omidahmadi.vpscontroller;

import ch.omidahmadi.vpscontroller.container.DockerClient;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
class DockerConfig {

    @Bean
    DockerClient dockerClient(@Value("${vps.docker.base-url}") String baseUrl,
                              @Value("${vps.docker.timeout:3s}") Duration timeout) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(timeout);
        // Boot 4 ships the RestClient.Builder auto-config in a separate starter; the plain builder is enough here.
        return new DockerClient(RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build());
    }
}

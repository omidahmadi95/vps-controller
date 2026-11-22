package ch.omidahmadi.vpscontroller.container;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** The fields we read from Docker's {@code GET /containers/json}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DockerContainer(
        @JsonProperty("Id") String id,
        @JsonProperty("Names") List<String> names,
        @JsonProperty("Image") String image,
        @JsonProperty("State") String state,
        @JsonProperty("Status") String status) {
}

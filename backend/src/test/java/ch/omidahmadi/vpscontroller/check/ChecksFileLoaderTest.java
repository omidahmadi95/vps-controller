package ch.omidahmadi.vpscontroller.check;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChecksFileLoaderTest {

    @TempDir
    Path dir;

    @Test
    void missingFileMeansZeroChecks() {
        assertThat(new ChecksFileLoader(dir.resolve("nope.yml").toString()).load()).isEmpty();
    }

    @Test
    void readsChecksAndAppliesDefaults() throws Exception {
        Path file = dir.resolve("checks.yml");
        Files.writeString(file, """
                checks:
                  - name: Shop
                    url: https://shop.example.org/health
                  - name: API
                    url: https://api.example.org/ping
                    interval: 30s
                    timeout: 5s
                    accept: ["200-299", "401"]
                    failureThreshold: 3
                    recoveryThreshold: 2
                """);

        List<CheckSpec> specs = new ChecksFileLoader(file.toString()).load();

        assertThat(specs).hasSize(2);
        CheckSpec shop = specs.getFirst();
        assertThat(shop.name()).isEqualTo("Shop");
        assertThat(shop.interval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(shop.timeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(shop.accept().describe()).isEqualTo("200-399");
        assertThat(shop.failureThreshold()).isEqualTo(2);
        assertThat(shop.recoveryThreshold()).isEqualTo(1);

        CheckSpec api = specs.get(1);
        assertThat(api.interval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(api.timeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(api.accept().describe()).isEqualTo("200-299, 401");
        assertThat(api.failureThreshold()).isEqualTo(3);
        assertThat(api.recoveryThreshold()).isEqualTo(2);
    }

    @Test
    void skipsBadEntriesButKeepsTheGoodOnes() throws Exception {
        Path file = dir.resolve("checks.yml");
        Files.writeString(file, """
                checks:
                  - name: Good
                    url: https://good.example.org/
                  - name: Broken
                    url:
                  - name: BadAccept
                    url: https://bad.example.org/
                    accept: ["nonsense"]
                """);

        List<CheckSpec> specs = new ChecksFileLoader(file.toString()).load();

        assertThat(specs).extracting(CheckSpec::name).containsExactly("Good");
    }
}

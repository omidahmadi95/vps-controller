package ch.omidahmadi.vpscontroller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = "vps.scheduler.enabled=false")
@Import(TestPostgres.class)
class VpsControllerApplicationTests {

	@Test
	void contextLoads() {
	}

}

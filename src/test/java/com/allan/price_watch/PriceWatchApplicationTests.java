package com.allan.price_watch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@EnabledIf("com.allan.price_watch.PriceWatchApplicationTests#dockerAvailable")
class PriceWatchApplicationTests {

	@Test
	void contextLoads() {
	}

	/**
	 * Context load needs Testcontainers (Postgres/Redis/RabbitMQ). Skip cleanly
	 * when Docker isn't running so unit tests still pass on bare machines.
	 */
	static boolean dockerAvailable() {
		try {
			Process process = new ProcessBuilder("docker", "info")
					.redirectErrorStream(true)
					.start();
			boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
			return finished && process.exitValue() == 0;
		} catch (Exception e) {
			return false;
		}
	}
}

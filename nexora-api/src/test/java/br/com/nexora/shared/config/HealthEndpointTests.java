package br.com.nexora.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

/**
 * Healthcheck do container: só /actuator/health, na porta de gerenciamento (nunca publicada nem encaminhada pelo
 * proxy), sem detalhes internos e sem nenhum outro endpoint do Actuator.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = { "management.server.port=0" })
@ActiveProfiles("prod")
class HealthEndpointTests {

	@Autowired
	private Environment environment;

	private HttpResponse<String> get(String path) throws Exception {
		String port = environment.getProperty("local.management.port");
		assertThat(port).isNotNull();
		return HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
				HttpResponse.BodyHandlers.ofString());
	}

	@Test
	void healthIsUpWithoutAuthenticationAndWithoutDetails() throws Exception {
		HttpResponse<String> response = get("/actuator/health");
		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("\"status\":\"UP\"");
		assertThat(response.body()).doesNotContain("postgres", "PostgreSQL", "jdbc", "diskSpace", "components", "details");
	}

	@Test
	void readinessAndLivenessProbesAreAvailable() throws Exception {
		assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200);
		assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(200);
	}

	@Test
	void noOtherActuatorEndpointIsExposed() throws Exception {
		for (String path : new String[] { "/actuator/env", "/actuator/beans", "/actuator/metrics", "/actuator/heapdump",
				"/actuator/mappings", "/actuator/configprops", "/actuator/loggers", "/actuator/info" }) {
			assertThat(get(path).statusCode()).as(path).isIn(401, 403, 404);
		}
	}
}

package br.com.nexora.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Política de origem: frontend e API ficam na MESMA origem (proxy), então nenhuma origem externa recebe permissão
 * cross-origin. Nunca "*", nunca eco do Origin recebido.
 */
@SpringBootTest
@ActiveProfiles("prod")
class CorsPolicyTests {

	private static final String EVIL = "https://evil.example";

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void preflightFromArbitraryOriginIsNotGranted() throws Exception {
		MvcResult result = mockMvc.perform(options("/api/v1/auth/login")
				.header(HttpHeaders.ORIGIN, EVIL)
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
				.andReturn();
		assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
		assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull();
	}

	@Test
	void actualCrossOriginResponseCarriesNoAllowOrigin() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
				.header(HttpHeaders.ORIGIN, EVIL)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"x@example.com\",\"password\":\"wrong-password-123\"}"))
				.andReturn();
		assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
	}

	@Test
	void noWildcardOriginIsEverAdvertised() throws Exception {
		MvcResult result = mockMvc.perform(options("/api/v1/jobs")
				.header(HttpHeaders.ORIGIN, "null")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")).andReturn();
		assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
	}
}

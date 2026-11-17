package br.com.nexora.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Perfil de produção: a configuração efetivamente selecionada (não só o YAML) não expõe ferramentas de
 * desenvolvimento, e as regras de autenticação continuam as mesmas.
 */
@SpringBootTest
@ActiveProfiles("prod")
class ProductionProfileTests {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private Environment environment;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void prodIsTheEffectiveProfileAndDevIsNotActive() {
		assertThat(environment.getActiveProfiles()).containsExactly("prod");
	}

	@Test
	void developmentLoggingIsNotEnabled() {
		assertThat(environment.getProperty("logging.level.br.com.nexora")).isEqualTo("INFO");
		assertThat(LoggerFactory.getLogger("br.com.nexora").isDebugEnabled()).isFalse();
	}

	@Test
	void openApiIsDisabledByConfigurationAndNotServed() throws Exception {
		assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean.class)).isFalse();
		assertThat(environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isFalse();
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
		mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
		mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
	}

	@Test
	void errorResponsesCarryNoStackTraceOrExceptionDetails() throws Exception {
		// Propriedades que só o application-prod.yaml define: provam que o YAML de produção foi carregado.
		assertThat(environment.getProperty("server.error.include-stacktrace")).isEqualTo("never");
		assertThat(environment.getProperty("server.error.include-message")).isEqualTo("never");
		assertThat(environment.getProperty("server.error.include-binding-errors")).isEqualTo("never");
		assertThat(environment.getProperty("spring.jpa.show-sql", Boolean.class)).isFalse();

		// Erro real: corpo JSON malformado em rota pública.
		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON).content("{not json"))
				.andExpect(status().isBadRequest())
				.andReturn();
		assertThat(result.getResponse().getContentAsString())
				.doesNotContain("Exception", "at br.com", "com.fasterxml", "JsonParse");
	}

	@Test
	void authenticationRulesAreUnchanged() throws Exception {
		mockMvc.perform(get("/api/v1/anything")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
	}
}

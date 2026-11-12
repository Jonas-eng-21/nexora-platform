package br.com.nexora.shared.domain;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import br.com.nexora.shared.security.TokenService;
import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.entity.UserRole;
import br.com.nexora.users.repository.UserRepository;

@SpringBootTest
@Transactional
class AreaCatalogTests {

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TokenService tokenService;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void authenticatedUsersListTheFixedAreaCatalogWithLabels() throws Exception {
		User student = userRepository.saveAndFlush(
				new User(UUID.randomUUID() + "@nexora.test", "hash", UserRole.STUDENT, AccountStatus.ACTIVE));

		mockMvc.perform(get("/api/v1/areas")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(student)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(ProfessionalArea.values().length))
				.andExpect(jsonPath("$[0].code").value("BACKEND"))
				.andExpect(jsonPath("$[0].label").value("Backend"))
				.andExpect(jsonPath("$[*].code", hasItem("DATA")))
				.andExpect(jsonPath("$[*].label", hasItem("Ciência e Análise de Dados")))
				.andExpect(jsonPath("$[*].code", hasItem("OTHER")));
	}

	@Test
	void areaCatalogRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/areas")).andExpect(status().isUnauthorized());
	}
}

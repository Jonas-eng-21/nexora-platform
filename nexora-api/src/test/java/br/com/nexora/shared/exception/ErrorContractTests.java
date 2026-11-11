package br.com.nexora.shared.exception;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class ErrorContractTests {

	@Autowired
	private WebApplicationContext context;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	@Test
	void validationErrorReturns400WithFieldErrors() throws Exception {
		mockMvc.perform(post("/test-errors/validation").with(user("tester"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name": "", "email": "not-an-email"}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.instance").value("/test-errors/validation"))
				.andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "email")))
				.andExpect(jsonPath("$.errors[0].message").isNotEmpty());
	}

	@Test
	void malformedJsonReturns400() throws Exception {
		mockMvc.perform(post("/test-errors/validation").with(user("tester"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{not json"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("BAD_REQUEST"));
	}

	@Test
	void unauthenticatedRequestReturns401() throws Exception {
		mockMvc.perform(get("/test-errors/not-found"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void accessDeniedReturns403WithoutInternalReason() throws Exception {
		mockMvc.perform(get("/test-errors/forbidden").with(user("tester")))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
				.andExpect(content().string(not(containsString("internal reason"))));
	}

	@Test
	void businessNotFoundReturns404WithStableCode() throws Exception {
		mockMvc.perform(get("/test-errors/not-found").with(user("tester")))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("JOB_NOT_FOUND"))
				.andExpect(jsonPath("$.detail").value("Vaga não encontrada."));
	}

	@Test
	void unknownRouteReturns404() throws Exception {
		mockMvc.perform(get("/no-such-route").with(user("tester")))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	void businessConflictReturns409WithStableCode() throws Exception {
		mockMvc.perform(get("/test-errors/conflict").with(user("tester")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
	}

	@Test
	void dataIntegrityViolationReturns409WithoutLeakingValues() throws Exception {
		mockMvc.perform(get("/test-errors/data-conflict").with(user("tester")))
				.andExpect(status().isConflict())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("DATA_CONFLICT"))
				.andExpect(content().string(not(containsString("secret@nexora.test"))));
	}

	@Test
	void unexpectedErrorReturns500WithoutInternalDetails() throws Exception {
		mockMvc.perform(get("/test-errors/unexpected").with(user("tester")))
				.andExpect(status().isInternalServerError())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(content().string(not(containsString("secret-internal-detail"))));
	}
}

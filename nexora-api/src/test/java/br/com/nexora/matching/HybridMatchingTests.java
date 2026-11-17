package br.com.nexora.matching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import jakarta.persistence.EntityManager;

import com.jayway.jsonpath.JsonPath;

import br.com.nexora.jobs.entity.Job;
import br.com.nexora.jobs.service.JobService;
import br.com.nexora.matching.semantic.FakeEmbeddingProvider;
import br.com.nexora.matching.semantic.HybridScore;
import br.com.nexora.matching.semantic.SemanticTestConfig;
import br.com.nexora.matching.service.MatchingService;
import br.com.nexora.shared.security.TokenService;
import br.com.nexora.students.entity.Student;
import br.com.nexora.students.service.StudentProfileService;
import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.entity.UserRole;
import br.com.nexora.users.repository.UserRepository;

/**
 * Matching híbrido com o semântico ligado e um provedor falso (sem rede). Cobre composição do score, fallback,
 * lotes e reaproveitamento de embeddings, e que só o currículo-base alimenta os textos.
 */
@SpringBootTest(properties = {
		"nexora.matching.semantic.enabled=true",
		"nexora.matching.semantic.model=" + SemanticTestConfig.MODEL,
		"nexora.matching.semantic.dimensions=" + SemanticTestConfig.DIMENSIONS,
		"GEMINI_API_KEY=test-key-not-real" })
@Import(SemanticTestConfig.class)
@Transactional
class HybridMatchingTests {

	private static final String MATCH = "/api/v1/matching/jobs/";

	@Autowired
	private WebApplicationContext context;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private TokenService tokenService;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private FakeEmbeddingProvider provider;
	@Autowired
	private MatchingService matchingService;
	@Autowired
	private StudentProfileService studentProfileService;
	@Autowired
	private JobService jobService;
	@Autowired
	private EntityManager entityManager;
	@Autowired
	private PlatformTransactionManager transactionManager;

	private MockMvc mockMvc;
	private UUID java;
	private UUID spring;
	private User company;
	private String jobId;

	@BeforeEach
	void setUp() throws Exception {
		clearEmbeddings();
		provider.reset();
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		java = skillId("java");
		spring = skillId("spring boot");
		company = createUser(UserRole.COMPANY);
		as(company, put("/api/v1/companies/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Tech Garanhuns\"}")).andExpect(status().isOk());
		jobId = createJob("Estágio em Backend Java", "Desenvolver APIs REST com Java e Spring Boot.");
	}

	@AfterEach
	void cleanUp() {
		// As gravações de embeddings têm transação própria e não são desfeitas pelo rollback do teste.
		clearEmbeddings();
		provider.reset();
	}

	/**
	 * Apaga em transação própria e já confirmada: um DELETE na transação do teste manteria as linhas travadas e
	 * a gravação (também em transação própria) do mesmo vetor ficaria esperando por ele para sempre.
	 */
	private void clearEmbeddings() {
		new TransactionTemplate(transactionManager, new DefaultTransactionDefinition(
				TransactionDefinition.PROPAGATION_REQUIRES_NEW)).executeWithoutResult(
						status -> jdbcTemplate.update("delete from text_embeddings where model = ?",
								SemanticTestConfig.MODEL));
	}

	@Test
	void scoreIsHybridAndExplainable() throws Exception {
		User student = createStudent("Desenvolvimento de APIs REST com Java e Spring Boot", "[\"BACKEND\"]", java,
				spring);

		String body = as(student, get(MATCH + jobId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.match.scoreType").value("HYBRID"))
				.andExpect(jsonPath("$.match.algorithmVersion").value("v1"))
				.andExpect(jsonPath("$.match.semanticScore").isNumber())
				.andExpect(jsonPath("$.match.criteria.length()").value(4))
				.andReturn().getResponse().getContentAsString();

		int score = JsonPath.read(body, "$.match.score");
		int structured = JsonPath.read(body, "$.match.structuredScore");
		int semantic = JsonPath.read(body, "$.match.semanticScore");
		assertThat(score).isEqualTo(HybridScore.combine(structured, semantic, 20)).isBetween(0, 100);
		assertThat(semantic).isBetween(0, 100);
		assertThat((String) JsonPath.read(body, "$.match.summary")).contains("semântica");
	}

	@Test
	void similarStudentScoresHigherSemanticallyThanAnUnrelatedOne() throws Exception {
		User close = createStudent("APIs REST com Java e Spring Boot para Backend", "[\"BACKEND\"]", java, spring);
		User far = createStudent("Design de interfaces e pesquisa com usuários em Figma", "[\"BACKEND\"]", java,
				spring);

		int closeSemantic = JsonPath.read(as(close, get(MATCH + jobId)).andReturn().getResponse().getContentAsString(),
				"$.match.semanticScore");
		int farSemantic = JsonPath.read(as(far, get(MATCH + jobId)).andReturn().getResponse().getContentAsString(),
				"$.match.semanticScore");

		assertThat(closeSemantic).isGreaterThan(farSemantic);
	}

	@Test
	void providerFailureFallsBackToTheStructuredScoreWithoutClaimingHybrid() throws Exception {
		User student = createStudent("APIs REST com Java", "[\"BACKEND\"]", java, spring);
		provider.failing(true);

		String body = as(student, get(MATCH + jobId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.match.scoreType").value("FALLBACK"))
				.andExpect(jsonPath("$.match.semanticScore").doesNotExist())
				.andReturn().getResponse().getContentAsString();

		assertThat((int) JsonPath.read(body, "$.match.score"))
				.isEqualTo((int) JsonPath.read(body, "$.match.structuredScore"));
		assertThat((String) JsonPath.read(body, "$.match.summary")).doesNotContainIgnoringCase("gemini")
				.doesNotContainIgnoringCase("google");
	}

	@Test
	void recommendationsEmbedTheWholePageInOneBatchAndReuseItAfterwards() throws Exception {
		User student = createStudent("APIs REST com Java e Spring Boot", "[\"BACKEND\"]", java, spring);
		for (int i = 1; i <= 4; i++) {
			createJob("Vaga " + i + " Java", "Descrição " + i + " de serviços Java");
		}

		as(student, get("/api/v1/matching/jobs?size=50"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(5))
				.andExpect(jsonPath("$.content[0].match.scoreType").value("HYBRID"))
				.andExpect(jsonPath("$.content[4].match.scoreType").value("HYBRID"));

		// 1 estudante + 5 vagas, em uma única requisição ao provedor (nunca uma por item da lista).
		assertThat(provider.calls()).hasSize(1);
		assertThat(provider.totalTexts()).isEqualTo(6);

		as(student, get("/api/v1/matching/jobs?size=50")).andExpect(status().isOk());
		assertThat(provider.calls()).hasSize(1);
	}

	@Test
	void recommendedJobsAreOrderedByTheHybridScore() throws Exception {
		User student = createStudent("APIs REST com Java e Spring Boot", "[\"BACKEND\"]", java, spring);
		createJob("Vaga paralela", "Outra vaga de Java");

		String body = as(student, get("/api/v1/matching/jobs?size=50")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		List<Integer> scores = JsonPath.read(body, "$.content[*].match.score");
		assertThat(scores).isSortedAccordingTo(Comparator.reverseOrder());
	}

	@Test
	void studentRecommendationsForACompanyUseOneBatchToo() throws Exception {
		makeEligible(createStudent("APIs REST com Java e Spring Boot", "[\"BACKEND\"]", java, spring));
		makeEligible(createStudent("Backend Java com Spring Boot", "[\"BACKEND\"]", java));
		makeEligible(createStudent("Dados e Python", "[\"DATA\"]", java));

		as(company, get("/api/v1/matching/jobs/" + jobId + "/students"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.content[0].match.scoreType").value("HYBRID"));

		assertThat(provider.calls()).hasSize(1);
		assertThat(provider.totalTexts()).isEqualTo(4); // 1 vaga + 3 estudantes
	}

	@Test
	void studentWithNothingToCompareStaysStructuredAndCallsNoProvider() throws Exception {
		User student = createStudent("Quero aprender", "[]");

		as(student, get(MATCH + jobId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.match.scoreType").value("STRUCTURED"));

		assertThat(provider.calls()).isEmpty();
	}

	@Test
	void currentScoresFollowTheSameRuleAsTheOtherEndpoints() throws Exception {
		User withData = createStudent("APIs REST com Java e Spring Boot", "[\"BACKEND\"]", java, spring);
		User withoutData = createStudent("Quero aprender", "[]");
		Student a = studentProfileService.getByUserId(withData.getId());
		Student b = studentProfileService.getByUserId(withoutData.getId());
		Job job = jobService.getOpenJob(UUID.fromString(jobId));

		List<Optional<Integer>> scores = matchingService.currentScores(job, List.of(a, b));

		assertThat(scores.get(0)).isPresent();
		assertThat(scores.get(1)).isEmpty(); // sem competências nem interesses: nulo, como na lista de candidaturas
		assertThat(provider.calls()).hasSize(1);
		assertThat(provider.totalTexts()).isEqualTo(2); // 1 vaga + 1 estudante com dados
	}

	@Test
	void changedBaseResumeGivesANewEmbeddingAndUnchangedOneReusesTheOld() throws Exception {
		User student = createStudent("APIs REST com Java", "[\"BACKEND\"]", java);
		as(student, get(MATCH + jobId)).andExpect(status().isOk());
		assertThat(provider.totalTexts()).isEqualTo(2); // estudante + vaga

		as(student, get(MATCH + jobId)).andExpect(status().isOk());
		assertThat(provider.totalTexts()).isEqualTo(2); // nada mudou: tudo reaproveitado

		as(student, put("/api/v1/students/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fullName\": \"Ana Souza\", \"summary\": \"Agora também com Kubernetes e nuvem\"}"))
				.andExpect(status().isOk());
		as(student, get(MATCH + jobId)).andExpect(status().isOk());

		assertThat(provider.totalTexts()).isEqualTo(3); // só o currículo novo foi embutido
	}

	@Test
	void textsSentToTheProviderCarryOnlyBaseResumeContent() throws Exception {
		User student = createStudent("APIs REST com Java", "[\"BACKEND\"]", java);
		as(student, put("/api/v1/students/me/resume").contentType(MediaType.APPLICATION_JSON).content("""
				{"interestAreas": ["BACKEND"],
				 "links": {"github": "https://github.com/ana-souza", "linkedin": "https://linkedin.com/in/ana"},
				 "projects": [{"name": "API de finanças", "description": "Controle de gastos"}]}"""))
				.andExpect(status().isOk());

		as(student, get(MATCH + jobId)).andExpect(status().isOk());

		String sent = provider.calls().stream().flatMap(List::stream).collect(Collectors.joining("\n"));
		assertThat(sent).contains("API de finanças");
		assertThat(sent).doesNotContain(student.getEmail()).doesNotContain("github.com").doesNotContain("linkedin")
				.doesNotContain("Ana Souza").doesNotContain("%PDF");
	}

	@Test
	void studentCannotUseCompanyRecommendationsEvenWithSemanticMatchingOn() throws Exception {
		User student = createStudent("APIs REST com Java", "[\"BACKEND\"]", java);

		as(student, get("/api/v1/matching/jobs/" + jobId + "/students")).andExpect(status().isForbidden());
	}

	// ---- apoio ----

	private String createJob(String title, String description) throws Exception {
		return JsonPath.read(as(company, post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content("""
				{"title": "%s", "description": "%s", "area": "BACKEND", "location": "Garanhuns - PE",
				 "workModel": "HYBRID", "level": "INTERNSHIP", "requiredSkillIds": ["%s", "%s"]}
				""".formatted(title, description, java, spring)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(), "$.id");
	}

	private User createStudent(String summary, String interestAreas, UUID... skills) throws Exception {
		User student = createUser(UserRole.STUDENT);
		as(student, put("/api/v1/students/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fullName\": \"Ana Souza\", \"location\": \"Garanhuns, PE\", \"summary\": \"%s\"}"
						.formatted(summary)))
				.andExpect(status().isOk());
		as(student, put("/api/v1/students/me/resume").contentType(MediaType.APPLICATION_JSON)
				.content("{\"interestAreas\": %s}".formatted(interestAreas)))
				.andExpect(status().isOk());
		String ids = Arrays.stream(skills).map(id -> "\"" + id + "\"").collect(Collectors.joining(","));
		as(student, put("/api/v1/students/me/skills").contentType(MediaType.APPLICATION_JSON)
				.content("{\"skillIds\": [" + ids + "]}"))
				.andExpect(status().isOk());
		return student;
	}

	/** Vínculo aprovado com universidade ativa: a regra de elegibilidade que a recomendação para empresas exige. */
	private void makeEligible(User student) {
		User universityUser = createUser(UserRole.UNIVERSITY);
		UUID universityId = jdbcTemplate.queryForObject(
				"INSERT INTO universities (user_id, name, approval_status) VALUES (?, 'UFAPE', 'APPROVED') RETURNING id",
				UUID.class, universityUser.getId());
		entityManager.flush();
		UUID studentId = jdbcTemplate.queryForObject("SELECT id FROM students WHERE user_id = ?", UUID.class,
				student.getId());
		jdbcTemplate.update("INSERT INTO university_affiliations (student_id, university_id, status) VALUES (?, ?, ?)",
				studentId, universityId, "APPROVED");
	}

	private UUID skillId(String normalizedName) {
		return jdbcTemplate.queryForObject("SELECT id FROM skills WHERE normalized_name = ?", UUID.class,
				normalizedName);
	}

	private User createUser(UserRole role) {
		return userRepository.saveAndFlush(
				new User(UUID.randomUUID() + "@nexora.test", "hash", role, AccountStatus.ACTIVE));
	}

	private ResultActions as(User user, MockHttpServletRequestBuilder request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(user)));
	}
}

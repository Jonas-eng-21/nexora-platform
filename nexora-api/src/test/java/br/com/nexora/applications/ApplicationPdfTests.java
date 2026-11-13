package br.com.nexora.applications;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

import br.com.nexora.shared.security.TokenService;
import br.com.nexora.users.entity.AccountStatus;
import br.com.nexora.users.entity.User;
import br.com.nexora.users.entity.UserRole;
import br.com.nexora.users.repository.UserRepository;
import jakarta.persistence.EntityManager;

@SpringBootTest
@Transactional
class ApplicationPdfTests {

	private static final int MAX_BYTES = 5_242_880;

	@Value("${nexora.storage.resumes-dir}")
	private Path storageDir;

	@Autowired
	private WebApplicationContext context;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	private MockMvc mockMvc;
	private User company;
	private UUID universityId;
	private String jobId;
	private User student;

	@BeforeEach
	void setUp() throws Exception {
		clearStorage();
		mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		company = createCompany("Tech Garanhuns");
		User universityAccount = createUser(UserRole.UNIVERSITY);
		universityId = jdbcTemplate.queryForObject(
				"INSERT INTO universities (user_id, name, approval_status) VALUES (?, 'UFAPE', 'APPROVED') RETURNING id",
				UUID.class, universityAccount.getId());
		jobId = publishJob(company, "Estágio Backend Java");
		student = validatedStudent("Ana Souza");
	}

	@AfterEach
	void clearStorage() throws IOException {
		if (Files.isDirectory(storageDir)) {
			try (Stream<Path> files = Files.list(storageDir)) {
				files.forEach(file -> {
					try {
						Files.delete(file);
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				});
			}
		}
	}

	@Test
	void validatedStudentAppliesWithAPdfAndItIsStoredPrivately() throws Exception {
		byte[] pdf = pdf(2_000);

		as(student, applyWithPdf(jobId, "curriculo-ana.pdf", "application/pdf", pdf))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.resumeType").value("PDF"))
				.andExpect(jsonPath("$.status").value("SUBMITTED"))
				.andExpect(jsonPath("$.jobTitle").value("Estágio Backend Java"))
				.andExpect(jsonPath("$.resumeFileName").value("curriculo-ana.pdf"))
				.andExpect(jsonPath("$.resumeFileSize").value(pdf.length))
				.andExpect(jsonPath("$.storageKey").doesNotExist());

		entityManager.flush();
		Map<String, Object> row = jdbcTemplate.queryForMap(
				"SELECT resume_type, resume_snapshot, storage_key, original_filename, file_size, content_type "
						+ "FROM applications");
		assertEquals("PDF", row.get("resume_type"));
		assertNull(row.get("resume_snapshot"));
		assertEquals("curriculo-ana.pdf", row.get("original_filename"));
		assertEquals((long) pdf.length, ((Number) row.get("file_size")).longValue());
		assertEquals("application/pdf", row.get("content_type"));
		String key = (String) row.get("storage_key");
		// Nome interno gerado pelo servidor: nada do que o cliente enviou.
		assertTrue(key.matches("[0-9a-f-]{36}\\.pdf"), key);
		assertNotEquals("curriculo-ana.pdf", key);
		assertArrayEquals(pdf, Files.readAllBytes(storageDir.resolve(key)));
		assertEquals(1, storedFiles().size());
	}

	@Test
	void pdfOfExactlyFiveMebibytesIsAcceptedAndOneByteMoreIsRejected() throws Exception {
		as(student, applyWithPdf(jobId, "limite.pdf", "application/pdf", pdf(MAX_BYTES)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.resumeFileSize").value(MAX_BYTES));

		User other = validatedStudent("Bruno Lima");
		as(other, applyWithPdf(jobId, "grande.pdf", "application/pdf", pdf(MAX_BYTES + 1)))
				.andExpect(status().isPayloadTooLarge())
				.andExpect(jsonPath("$.code").value("RESUME_FILE_TOO_LARGE"));
		assertEquals(1, applicationCount());
		assertEquals(1, storedFiles().size());
	}

	@Test
	void invalidFilesAreRejectedAndNothingIsStored() throws Exception {
		byte[] notAPdf = "Isto é um texto, não um PDF.".getBytes(StandardCharsets.UTF_8);
		as(student, applyWithPdf(jobId, "falso.pdf", "application/pdf", notAPdf))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_RESUME_FILE"));
		as(student, applyWithPdf(jobId, "cv.pdf", "application/octet-stream", pdf(100)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_RESUME_FILE"));
		as(student, applyWithPdf(jobId, "cv.txt", "application/pdf", pdf(100)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_RESUME_FILE"));
		as(student, applyWithPdf(jobId, "vazio.pdf", "application/pdf", new byte[0]))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_RESUME_FILE"));

		assertEquals(0, applicationCount());
		assertEquals(0, storedFiles().size());
	}

	@Test
	void pdfModalityRequiresAFileAndPlatformModalityForbidsOne() throws Exception {
		as(student, multipart("/api/v1/jobs/" + jobId + "/applications").param("resumeType", "PDF"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("RESUME_FILE_REQUIRED"));

		as(student, multipart("/api/v1/jobs/" + jobId + "/applications").file(file("cv.pdf", "application/pdf", pdf(100)))
				.param("resumeType", "PLATFORM"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("RESUME_FILE_NOT_ALLOWED"));

		assertEquals(0, applicationCount());
		assertEquals(0, storedFiles().size());
	}

	@Test
	void originalFilenameIsReducedToASafeName() throws Exception {
		User s2 = validatedStudent("Bruno Lima");
		User s3 = validatedStudent("Carla Dias");
		as(student, applyWithPdf(jobId, "../../etc/passwd.pdf", "application/pdf", pdf(100)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.resumeFileName").value("passwd.pdf"));
		as(s2, applyWithPdf(jobId, "C:\\Users\\bruno\\cv bruno.pdf", "application/pdf", pdf(100)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.resumeFileName").value("cv bruno.pdf"));
		as(s3, applyWithPdf(jobId, "cv\r\nSet-Cookie: x=1.PDF", "application/pdf", pdf(100)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.resumeFileName").value("cvSet-Cookie: x=1.PDF"));

		// Nada fora da pasta de armazenamento e todos com nome gerado.
		assertEquals(3, storedFiles().size());
		assertTrue(storedFiles().stream().allMatch(name -> name.matches("[0-9a-f-]{36}\\.pdf")));
	}

	@Test
	void secondApplicationWithAPdfLeavesNoOrphanFile() throws Exception {
		as(student, applyWithPdf(jobId, "a.pdf", "application/pdf", pdf(100))).andExpect(status().isCreated());

		as(student, applyWithPdf(jobId, "b.pdf", "application/pdf", pdf(100)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("APPLICATION_ALREADY_EXISTS"));
		assertEquals(1, applicationCount());
		assertEquals(1, storedFiles().size());
	}

	@Test
	void pdfApplicationRequiresEligibilityJustLikeThePlatformOne() throws Exception {
		User unvalidated = createStudentProfile("Sem vínculo");
		as(unvalidated, applyWithPdf(jobId, "cv.pdf", "application/pdf", pdf(100)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("STUDENT_NOT_VALIDATED"));
		assertEquals(0, storedFiles().size());
	}

	@Test
	void theMatchScoreIgnoresTheSubmittedPdf() throws Exception {
		String before = as(student, get("/api/v1/matching/jobs/" + jobId)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		as(student, applyWithPdf(jobId, "java-python-docker.pdf", "application/pdf",
				("%PDF-1.4\n Java Python Docker Kubernetes\n%%EOF").getBytes(StandardCharsets.US_ASCII)))
				.andExpect(status().isCreated());

		String after = as(student, get("/api/v1/matching/jobs/" + jobId)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertEquals(before, after);
	}

	@Test
	void studentCompanyAndAdminCanDownloadThePdf() throws Exception {
		byte[] pdf = pdf(3_000);
		String id = apply(student, "curriculo ana.pdf", pdf);

		for (User reader : List.of(student, company, createUser(UserRole.ADMIN))) {
			byte[] body = as(reader, get("/api/v1/applications/" + id + "/resume"))
					.andExpect(status().isOk())
					.andExpect(content().contentType("application/pdf"))
					.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
							org.hamcrest.Matchers.startsWith("attachment")))
					.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
							org.hamcrest.Matchers.containsString("curriculo ana.pdf")))
					.andExpect(header().string("X-Content-Type-Options", "nosniff"))
					.andExpect(header().string(HttpHeaders.CACHE_CONTROL,
							org.hamcrest.Matchers.containsString("no-store")))
					.andReturn().getResponse().getContentAsByteArray();
			assertArrayEquals(pdf, body);
		}
	}

	@Test
	void usersWithoutAccessToTheApplicationGetNotFoundOrForbidden() throws Exception {
		String id = apply(student, "cv.pdf", pdf(500));
		String path = "/api/v1/applications/" + id + "/resume";

		as(validatedStudent("Outro Estudante"), get(path))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
		as(createCompany("Outra Empresa"), get(path))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
		as(createUser(UserRole.UNIVERSITY), get(path)).andExpect(status().isForbidden());
		mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
		as(student, get("/api/v1/applications/" + UUID.randomUUID() + "/resume"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("APPLICATION_NOT_FOUND"));
	}

	@Test
	void companyStillReadsThePdfAfterTheStudentLosesTheAffiliation() throws Exception {
		byte[] pdf = pdf(800);
		String id = apply(student, "cv.pdf", pdf);
		entityManager.flush();
		jdbcTemplate.update("UPDATE university_affiliations SET status = 'REVOKED' WHERE student_id = ?",
				studentProfileId(student));
		entityManager.clear();

		byte[] body = as(company, get("/api/v1/applications/" + id + "/resume")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsByteArray();
		assertArrayEquals(pdf, body);
	}

	@Test
	void databaseKeepsSnapshotAndPdfMutuallyExclusive() {
		UUID studentId = studentProfileId(student);
		entityManager.flush();
		String bothSet = "INSERT INTO applications (student_id, job_id, resume_type, status, resume_snapshot, "
				+ "storage_key, original_filename, file_size, content_type) "
				+ "VALUES (?, ?, 'PDF', 'SUBMITTED', '{}'::jsonb, 'k.pdf', 'cv.pdf', 10, 'application/pdf')";
		org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
				() -> jdbcTemplate.update(bothSet, studentId, UUID.fromString(jobId)));
	}

	private String apply(User owner, String filename, byte[] pdf) throws Exception {
		String response = as(owner, applyWithPdf(jobId, filename, "application/pdf", pdf))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	private static AbstractMockHttpServletRequestBuilder<?> applyWithPdf(String job, String filename, String contentType,
			byte[] bytes) {
		return multipart("/api/v1/jobs/" + job + "/applications").file(file(filename, contentType, bytes))
				.param("resumeType", "PDF");
	}

	private static MockMultipartFile file(String filename, String contentType, byte[] bytes) {
		return new MockMultipartFile("file", filename, contentType, bytes);
	}

	/** PDF mínimo válido para os testes: assinatura, lixo até o tamanho pedido e marcador de fim. */
	private static byte[] pdf(int size) {
		byte[] bytes = new byte[size];
		Arrays.fill(bytes, (byte) ' ');
		byte[] header = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);
		System.arraycopy(header, 0, bytes, 0, Math.min(header.length, size));
		return bytes;
	}

	private List<String> storedFiles() throws IOException {
		if (!Files.isDirectory(storageDir)) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(storageDir)) {
			return files.map(file -> file.getFileName().toString()).toList();
		}
	}

	private long applicationCount() {
		entityManager.flush();
		return jdbcTemplate.queryForObject("SELECT count(*) FROM applications", Long.class);
	}

	private UUID studentProfileId(User owner) {
		entityManager.flush();
		return jdbcTemplate.queryForObject("SELECT id FROM students WHERE user_id = ?", UUID.class, owner.getId());
	}

	private User validatedStudent(String name) throws Exception {
		User owner = createStudentProfile(name);
		jdbcTemplate.update("INSERT INTO university_affiliations (student_id, university_id, status) VALUES (?, ?, 'APPROVED')",
				studentProfileId(owner), universityId);
		return owner;
	}

	private User createStudentProfile(String name) throws Exception {
		User owner = createUser(UserRole.STUDENT);
		as(owner, put("/api/v1/students/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"fullName\": \"%s\"}".formatted(name))).andExpect(status().isOk());
		return owner;
	}

	private String publishJob(User owner, String title) throws Exception {
		UUID java = jdbcTemplate.queryForObject("SELECT id FROM skills WHERE normalized_name = 'java'", UUID.class);
		String response = as(owner, post("/api/v1/jobs").contentType(MediaType.APPLICATION_JSON).content("""
				{"title": "%s", "description": "Descrição da vaga.", "area": "BACKEND", "location": null,
				 "workModel": "REMOTE", "level": "INTERNSHIP", "requiredSkillIds": ["%s"], "desiredSkillIds": []}
				""".formatted(title, java)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.id");
	}

	private User createUser(UserRole role) {
		return userRepository.saveAndFlush(
				new User(UUID.randomUUID() + "@nexora.test", "hash", role, AccountStatus.ACTIVE));
	}

	private User createCompany(String name) throws Exception {
		User owner = createUser(UserRole.COMPANY);
		as(owner, put("/api/v1/companies/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"%s\"}".formatted(name)))
				.andExpect(status().isOk());
		return owner;
	}

	private ResultActions as(User user, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
		return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issue(user)));
	}
}

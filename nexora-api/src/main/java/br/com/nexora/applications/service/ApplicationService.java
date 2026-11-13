package br.com.nexora.applications.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.com.nexora.applications.dto.ApplicationResponse;
import br.com.nexora.applications.dto.CreateApplicationRequest;
import br.com.nexora.applications.entity.Application;
import br.com.nexora.applications.entity.ResumeType;
import br.com.nexora.applications.repository.ApplicationRepository;
import br.com.nexora.jobs.entity.Job;
import br.com.nexora.jobs.service.JobService;
import br.com.nexora.shared.exception.ApiException;
import br.com.nexora.shared.security.AuthenticatedUser;
import br.com.nexora.students.entity.Student;
import br.com.nexora.students.service.StudentProfileService;
import br.com.nexora.universities.service.AcademicEligibilityService;

@Service
public class ApplicationService {

	/** 5 MiB, o limite do escopo. O servidor confere aqui mesmo que o contêiner HTTP já limite o upload. */
	static final long MAX_PDF_BYTES = 5L * 1024 * 1024;

	private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
	private static final String PDF_CONTENT_TYPE = "application/pdf";
	private static final int MAX_FILENAME_LENGTH = 150;

	private final ApplicationRepository applicationRepository;
	private final StudentProfileService studentProfileService;
	private final AcademicEligibilityService eligibilityService;
	private final JobService jobService;
	private final ResumeFileStorage fileStorage;

	public ApplicationService(ApplicationRepository applicationRepository,
			StudentProfileService studentProfileService, AcademicEligibilityService eligibilityService,
			JobService jobService, ResumeFileStorage fileStorage) {
		this.applicationRepository = applicationRepository;
		this.studentProfileService = studentProfileService;
		this.eligibilityService = eligibilityService;
		this.jobService = jobService;
		this.fileStorage = fileStorage;
	}

	/**
	 * Candidatura do estudante autenticado. As regras de elegibilidade e de vaga aberta vêm dos pontos únicos
	 * ({@link AcademicEligibilityService} e {@link JobService}); a duplicidade é barrada aqui e, sob concorrência,
	 * pela unicidade (estudante, vaga) do banco. O documento é exclusivo: snapshot (PLATFORM) ou PDF.
	 */
	@Transactional
	public ApplicationResponse apply(UUID studentUserId, UUID jobId, CreateApplicationRequest request) {
		Student student = studentProfileService.getByUserId(studentUserId);
		eligibilityService.requireEligible(student.getId());
		Job job = jobService.requireAcceptingApplications(jobId);
		if (applicationRepository.existsByStudentIdAndJobId(student.getId(), job.getId())) {
			throw new ApiException(HttpStatus.CONFLICT, "APPLICATION_ALREADY_EXISTS",
					"Você já se candidatou a esta vaga.");
		}
		Application application = request.resumeType() == ResumeType.PDF
				? withPdf(student, job, request.file())
				: withPlatformResume(student, job, request.file());
		return ApplicationResponse.from(application);
	}

	private Application withPlatformResume(Student student, Job job, MultipartFile file) {
		if (hasFile(file)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "RESUME_FILE_NOT_ALLOWED",
					"Não envie arquivo com o currículo da plataforma: escolha apenas uma das opções.");
		}
		return applicationRepository.saveAndFlush(Application.withPlatformResume(student, job));
	}

	private Application withPdf(Student student, Job job, MultipartFile file) {
		if (!hasFile(file)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "RESUME_FILE_REQUIRED",
					"Envie o arquivo PDF no campo \"file\".");
		}
		String filename = safeFilename(file.getOriginalFilename());
		byte[] content = validatedPdf(file, filename);

		String key = fileStorage.store(content);
		try {
			return applicationRepository.saveAndFlush(
					Application.withPdfResume(student, job, key, filename, content.length, PDF_CONTENT_TYPE));
		} catch (RuntimeException e) {
			// O arquivo só existe para esta candidatura: sem a linha no banco, ele não pode ficar para trás.
			fileStorage.deleteQuietly(key);
			throw e;
		}
	}

	/** PDF válido: tamanho, extensão, tipo declarado e assinatura (RNF-11). Nada é gravado antes de passar. */
	private static byte[] validatedPdf(MultipartFile file, String filename) {
		if (file.getSize() > MAX_PDF_BYTES) {
			throw tooLarge();
		}
		if (!filename.toLowerCase(Locale.ROOT).endsWith(".pdf") || !isPdfContentType(file.getContentType())) {
			throw invalid("O arquivo deve ser um PDF (extensão .pdf e tipo application/pdf).");
		}
		byte[] content;
		try {
			content = file.getBytes();
		} catch (IOException e) {
			throw new UncheckedIOException("Falha ao ler o arquivo enviado", e);
		}
		if (content.length > MAX_PDF_BYTES) {
			throw tooLarge();
		}
		if (content.length < PDF_SIGNATURE.length
				|| !Arrays.equals(content, 0, PDF_SIGNATURE.length, PDF_SIGNATURE, 0, PDF_SIGNATURE.length)) {
			throw invalid("O conteúdo do arquivo não é um PDF válido.");
		}
		return content;
	}

	private static boolean isPdfContentType(String contentType) {
		return contentType != null
				&& contentType.split(";", 2)[0].strip().equalsIgnoreCase(PDF_CONTENT_TYPE);
	}

	/** Um navegador sem arquivo escolhido envia a parte vazia e sem nome: isso equivale a "sem arquivo". */
	private static boolean hasFile(MultipartFile file) {
		return file != null && !(file.isEmpty() && (file.getOriginalFilename() == null
				|| file.getOriginalFilename().isBlank()));
	}

	/**
	 * Nome original só para exibição: fica apenas o último trecho do caminho, sem caracteres de controle.
	 * Nunca vira nome de arquivo no servidor.
	 */
	static String safeFilename(String original) {
		if (original == null) {
			return "";
		}
		String name = original.replace('\\', '/');
		name = name.substring(name.lastIndexOf('/') + 1);
		name = name.chars().filter(c -> !Character.isISOControl(c))
				.collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString().strip();
		return name.length() > MAX_FILENAME_LENGTH ? name.substring(name.length() - MAX_FILENAME_LENGTH) : name;
	}

	private static ApiException invalid(String detail) {
		return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RESUME_FILE", detail);
	}

	private static ApiException tooLarge() {
		return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "RESUME_FILE_TOO_LARGE",
				"O PDF excede o limite de 5 MiB (5.242.880 bytes).");
	}

	/** PDF de uma candidatura para o dono, a empresa da vaga ou o admin; os demais recebem 404. */
	@Transactional(readOnly = true)
	public ResumeDownload downloadResume(AuthenticatedUser principal, UUID applicationId) {
		Application application = applicationRepository.findById(applicationId)
				.filter(found -> canRead(principal, found))
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND",
						"Candidatura não encontrada."));
		if (application.getResumeType() != ResumeType.PDF) {
			throw new ApiException(HttpStatus.NOT_FOUND, "RESUME_FILE_NOT_FOUND",
					"Esta candidatura não tem um PDF: o currículo enviado é o da plataforma.");
		}
		return new ResumeDownload(application.getOriginalFilename(), application.getContentType(),
				fileStorage.read(application.getStorageKey()));
	}

	private static boolean canRead(AuthenticatedUser principal, Application application) {
		return switch (principal.role()) {
			case ADMIN -> true;
			case STUDENT -> application.getStudent().getUserId().equals(principal.id());
			case COMPANY -> application.getJob().getCompany().getUserId().equals(principal.id());
			case UNIVERSITY -> false;
		};
	}

	/** O arquivo para o download, com o nome original saneado e o tipo gravado na candidatura. */
	public record ResumeDownload(String filename, String contentType, byte[] content) {
	}
}

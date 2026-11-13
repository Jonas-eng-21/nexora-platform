package br.com.nexora.applications.entity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import br.com.nexora.jobs.entity.Job;
import br.com.nexora.students.entity.Student;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Candidatura de um estudante a uma vaga. Sem cascatas: o histórico nunca é apagado junto com outra entidade. */
@Entity
@Table(name = "applications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Application {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "student_id", nullable = false, updatable = false)
	private Student student;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "job_id", nullable = false, updatable = false)
	private Job job;

	@Enumerated(EnumType.STRING)
	@Column(name = "resume_type", nullable = false, updatable = false, length = 20)
	private ResumeType resumeType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ApplicationStatus status;

	/** Imutável: sem setter e fora de qualquer UPDATE. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "resume_snapshot", updatable = false)
	private ResumeSnapshot resumeSnapshot;

	/** Nome interno do PDF no armazenamento, gerado pelo servidor. Nunca exposto pela API. */
	@Column(name = "storage_key", updatable = false, length = 100)
	private String storageKey;

	@Column(name = "original_filename", updatable = false, length = 150)
	private String originalFilename;

	@Column(name = "file_size", updatable = false)
	private Long fileSize;

	@Column(name = "content_type", updatable = false, length = 100)
	private String contentType;

	/** Motivo informado na última mudança de andamento (obrigatório ao rejeitar); nulo se não houve. */
	@Column(name = "status_reason", length = 500)
	private String statusReason;

	// Definida no construtor (e não por @CreationTimestamp) para já constar na resposta de criação.
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** Candidatura com o currículo da plataforma: o snapshot é capturado agora e não muda mais. */
	public static Application withPlatformResume(Student student, Job job) {
		Application application = new Application();
		application.student = student;
		application.job = job;
		application.resumeType = ResumeType.PLATFORM;
		application.status = ApplicationStatus.SUBMITTED;
		application.resumeSnapshot = ResumeSnapshot.of(student);
		application.createdAt = now();
		return application;
	}

	/** Candidatura com um PDF próprio já gravado no armazenamento privado. Não há snapshot. */
	public static Application withPdfResume(Student student, Job job, String storageKey, String originalFilename,
			long fileSize, String contentType) {
		Application application = new Application();
		application.student = student;
		application.job = job;
		application.resumeType = ResumeType.PDF;
		application.status = ApplicationStatus.SUBMITTED;
		application.storageKey = storageKey;
		application.originalFilename = originalFilename;
		application.fileSize = fileSize;
		application.contentType = contentType;
		application.createdAt = now();
		return application;
	}

	/**
	 * Registra a mudança de andamento e o motivo. Quem chama garante que a transição é permitida
	 * ({@link ApplicationStatus#canMoveTo}); o motivo já vem sem espaços nas pontas, ou nulo.
	 */
	public void changeStatus(ApplicationStatus newStatus, String reason) {
		this.status = newStatus;
		this.statusReason = reason;
	}

	/** Microssegundos: a mesma precisão que o PostgreSQL guarda, para a data não mudar ao ser relida. */
	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}
}

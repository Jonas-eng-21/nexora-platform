package br.com.nexora.jobs.entity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.UpdateTimestamp;

import br.com.nexora.companies.entity.Company;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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

/** Vaga de uma empresa. A empresa dona é fixada na criação e nunca muda; a exclusão é sempre lógica. */
@Entity
@Table(name = "jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Job {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "company_id", nullable = false, updatable = false)
	private Company company;

	@Column(nullable = false, length = 150)
	private String title;

	@Column(nullable = false, length = 5000)
	private String description;

	@Column(nullable = false, length = 100)
	private String area;

	@Column(length = 150)
	private String location;

	@Enumerated(EnumType.STRING)
	@Column(name = "work_model", nullable = false, length = 20)
	private WorkModel workModel;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private JobLevel level;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private JobStatus status;

	@ElementCollection
	@CollectionTable(name = "job_skills", joinColumns = @JoinColumn(name = "job_id"))
	private Set<JobSkill> skills = new HashSet<>();

	// Definido na criação (e não por @CreationTimestamp) para já constar na resposta do POST.
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	public Job(Company company, JobStatus status) {
		this.company = company;
		this.status = status;
		this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
		this.updatedAt = this.createdAt;
	}

	/** Substituição integral dos dados descritivos: campo opcional ausente ou em branco é gravado como nulo. */
	public void updateDetails(String title, String description, String area, String location, WorkModel workModel,
			JobLevel level) {
		this.title = title.strip();
		this.description = description.strip();
		this.area = area.strip();
		this.location = location == null || location.isBlank() ? null : location.strip();
		this.workModel = workModel;
		this.level = level;
	}

	public void changeStatus(JobStatus status) {
		this.status = status;
	}

	public void replaceSkills(Collection<JobSkill> newSkills) {
		skills.clear();
		skills.addAll(newSkills);
	}

	/** Exclusão lógica: encerra a vaga e registra a data sem apagar a linha nem candidaturas vinculadas. */
	public void markDeleted() {
		this.status = JobStatus.CLOSED;
		this.deletedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
	}
}

package br.com.nexora.universities.entity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.hibernate.annotations.UpdateTimestamp;

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

/** Um pedido de vínculo acadêmico e seu desfecho. Sem cascatas: o histórico nunca é apagado junto com outra entidade. */
@Entity
@Table(name = "university_affiliations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UniversityAffiliation {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "student_id", nullable = false, updatable = false)
	private Student student;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "university_id", nullable = false, updatable = false)
	private University university;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AffiliationStatus status;

	// Definida no construtor (e não por @CreationTimestamp) para já constar na resposta de criação.
	@Column(name = "requested_at", nullable = false, updatable = false)
	private Instant requestedAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/** Todo vínculo nasce como solicitação pendente. */
	public UniversityAffiliation(Student student, University university) {
		this.student = student;
		this.university = university;
		this.status = AffiliationStatus.PENDING;
		// Microssegundos: a mesma precisão que o PostgreSQL guarda, para a data não mudar ao ser relida.
		this.requestedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
	}
}

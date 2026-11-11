package br.com.nexora.students.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import br.com.nexora.students.resume.Resume;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "students")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Student {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "full_name", nullable = false, length = 150)
	private String fullName;

	@Column(length = 150)
	private String course;

	@Column(length = 150)
	private String institution;

	@Column(length = 150)
	private String location;

	@Column(length = 2000)
	private String summary;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private Resume resume = Resume.empty();

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public Student(UUID userId) {
		this.userId = userId;
	}

	/** Substituição integral: campo opcional ausente ou em branco é gravado como nulo. */
	public void updateProfile(String fullName, String course, String institution, String location, String summary) {
		this.fullName = fullName.strip();
		this.course = blankToNull(course);
		this.institution = blankToNull(institution);
		this.location = blankToNull(location);
		this.summary = blankToNull(summary);
	}

	public void replaceResume(Resume resume) {
		this.resume = resume;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}
}

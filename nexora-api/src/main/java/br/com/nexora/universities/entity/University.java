package br.com.nexora.universities.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
@Table(name = "universities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class University {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(nullable = false, length = 150)
	private String name;

	@Column(length = 30)
	private String acronym;

	@Column(length = 150)
	private String location;

	@Column(length = 300)
	private String website;

	@Column(length = 2000)
	private String description;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public University(UUID userId) {
		this.userId = userId;
	}

	/** Substituição integral: campo opcional ausente ou em branco é gravado como nulo. */
	public void updateProfile(String name, String acronym, String location, String website, String description) {
		this.name = name.strip();
		this.acronym = blankToNull(acronym);
		this.location = blankToNull(location);
		this.website = blankToNull(website);
		this.description = blankToNull(description);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}
}

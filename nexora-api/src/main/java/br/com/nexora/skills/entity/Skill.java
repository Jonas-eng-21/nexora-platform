package br.com.nexora.skills.entity;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

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
@Table(name = "skills")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Skill {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, updatable = false, length = 80)
	private String name;

	@Column(name = "normalized_name", nullable = false, updatable = false, length = 80)
	private String normalizedName;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public Skill(String name) {
		this.name = name.strip().replaceAll("\\s+", " ");
		this.normalizedName = normalize(name);
	}

	/** Chave de comparação: minúsculas, sem acentos e com espaços simples. */
	public static String normalize(String name) {
		String withoutAccents = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		return withoutAccents.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}
}

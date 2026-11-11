package br.com.nexora.students.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Substituição integral do perfil: campos opcionais ausentes são limpos. */
public record UpdateStudentProfileRequest(
		@NotBlank @Size(max = 150) String fullName,
		@Size(max = 150) String course,
		@Schema(description = "Instituição informada pelo estudante. Não equivale ao vínculo acadêmico validado.")
		@Size(max = 150) String institution,
		@Size(max = 150) String location,
		@Size(max = 2000) String summary) {
}

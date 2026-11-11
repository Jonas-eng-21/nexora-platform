package br.com.nexora.authentication.dto;

import br.com.nexora.universities.dto.UniversityProfileRequest;
import br.com.nexora.users.entity.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @Size(min = 8, max = 72) String password,
		@Schema(description = "Perfil da conta. ADMIN não é aceito no cadastro público.",
				allowableValues = { "STUDENT", "COMPANY", "UNIVERSITY" })
		@NotNull UserRole role,
		@Schema(description = "Dados institucionais. Obrigatório para UNIVERSITY e não aceito para os demais perfis.")
		@Valid UniversityProfileRequest university) {
}

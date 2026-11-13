package br.com.nexora.applications.dto;

import org.springframework.web.multipart.MultipartFile;

import br.com.nexora.applications.entity.ResumeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Enviado como multipart/form-data: o campo {@code file} só vale (e é obrigatório) para {@code PDF}. */
public record CreateApplicationRequest(
		@Schema(description = "Documento enviado, exclusivo: PLATFORM (snapshot do currículo-base) ou PDF.")
		@NotNull ResumeType resumeType,
		@Schema(description = "Um único arquivo PDF de até 5 MiB (5.242.880 bytes). Obrigatório com PDF; "
				+ "não permitido com PLATFORM.", type = "string", format = "binary")
		MultipartFile file) {
}

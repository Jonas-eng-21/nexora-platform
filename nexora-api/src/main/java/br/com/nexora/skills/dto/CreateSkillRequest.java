package br.com.nexora.skills.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSkillRequest(@NotBlank @Size(max = 80) String name) {
}

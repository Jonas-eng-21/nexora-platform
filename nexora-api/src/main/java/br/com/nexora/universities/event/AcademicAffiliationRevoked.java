package br.com.nexora.universities.event;

import java.util.UUID;

/**
 * Publicado, dentro da transação da revogação, quando um vínculo aprovado é revogado (pela universidade ou pelo
 * admin). Quem precisa reagir (ex.: candidaturas) escuta aqui, sem que {@code universities} conheça esses módulos.
 *
 * @param studentId id do perfil de estudante (tabela students), não da conta
 */
public record AcademicAffiliationRevoked(UUID studentId) {
}

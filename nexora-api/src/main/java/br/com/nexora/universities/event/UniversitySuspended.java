package br.com.nexora.universities.event;

import java.util.UUID;

/**
 * Publicado, dentro da transação da suspensão, quando a conta de uma universidade ativa é suspensa. Os vínculos
 * não são alterados: continuam aprovados, mas deixam de conferir elegibilidade enquanto a conta estiver suspensa.
 *
 * @param universityUserId id da conta da universidade (tabela users)
 */
public record UniversitySuspended(UUID universityUserId) {
}

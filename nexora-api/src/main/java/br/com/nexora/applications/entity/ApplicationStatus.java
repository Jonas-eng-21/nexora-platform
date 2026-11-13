package br.com.nexora.applications.entity;

/** Andamento da candidatura (ESCOPO_MVP 3.5). Toda candidatura nasce SUBMITTED. */
public enum ApplicationStatus {
	SUBMITTED, IN_REVIEW, APPROVED, REJECTED, SUSPENDED;

	/**
	 * Transições que a empresa (ou o admin) pode fazer: SUBMITTED vai para IN_REVIEW, APPROVED ou REJECTED, e
	 * IN_REVIEW vai para APPROVED ou REJECTED. APPROVED e REJECTED são finais, e SUSPENDED só sai por uma
	 * reativação explícita (história própria): nenhum deles leva a lugar algum por aqui, nem a si mesmo.
	 */
	/** Em andamento: ainda pode avançar, e é o que a suspensão automática alcança (e a reativação restaura). */
	public boolean isInProgress() {
		return this == SUBMITTED || this == IN_REVIEW;
	}

	public boolean canMoveTo(ApplicationStatus target) {
		return switch (this) {
			case SUBMITTED -> target == IN_REVIEW || target == APPROVED || target == REJECTED;
			case IN_REVIEW -> target == APPROVED || target == REJECTED;
			case APPROVED, REJECTED, SUSPENDED -> false;
		};
	}
}

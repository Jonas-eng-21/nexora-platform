package br.com.nexora.shared.domain;

/**
 * Catálogo fixo de áreas de atuação, compartilhado pela vaga (área da vaga) e pelo currículo-base (áreas de
 * interesse do estudante). O matching compara as duas por igualdade, por isso não há texto livre.
 * <p>
 * Os nomes das constantes são gravados no banco e em snapshots: acrescente valores novos, mas não renomeie
 * nem remova os existentes sem uma migração.
 */
public enum ProfessionalArea {

	BACKEND("Backend"),
	FRONTEND("Frontend"),
	FULLSTACK("Full Stack"),
	MOBILE("Desenvolvimento Mobile"),
	DATA("Ciência e Análise de Dados"),
	ARTIFICIAL_INTELLIGENCE("Inteligência Artificial e Machine Learning"),
	DEVOPS_CLOUD("DevOps e Cloud"),
	INFRASTRUCTURE("Infraestrutura e Redes"),
	SECURITY("Segurança da Informação"),
	QA("Qualidade e Testes"),
	UX_UI("Design UX/UI"),
	EMBEDDED_IOT("Sistemas Embarcados e IoT"),
	GAMES("Desenvolvimento de Jogos"),
	PRODUCT_MANAGEMENT("Produto e Gestão de Projetos"),
	SUPPORT("Suporte Técnico"),
	OTHER("Outra");

	private final String label;

	ProfessionalArea(String label) {
		this.label = label;
	}

	/** Nome de exibição em português. */
	public String getLabel() {
		return label;
	}
}

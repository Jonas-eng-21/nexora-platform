package br.com.nexora.applications.entity;

/** Motivo da suspensão automática de uma candidatura, como código estável (nunca texto livre). */
public enum SuspensionReason {
	/** O vínculo acadêmico do estudante foi revogado. */
	ACADEMIC_AFFILIATION_REVOKED,
	/** A universidade do estudante foi suspensa; o vínculo segue aprovado, porém inelegível. */
	UNIVERSITY_SUSPENDED
}

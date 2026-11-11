package br.com.nexora.users.entity;

public enum AccountStatus {
	ACTIVE,
	PENDING_APPROVAL,
	SUSPENDED,
	/** Cadastro de universidade recusado pelo administrador. */
	REJECTED
}

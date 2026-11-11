package br.com.nexora.companies.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.nexora.companies.dto.UpdateCompanyProfileRequest;
import br.com.nexora.companies.entity.Company;
import br.com.nexora.companies.repository.CompanyRepository;
import br.com.nexora.shared.exception.ApiException;

@Service
public class CompanyProfileService {

	private final CompanyRepository companyRepository;

	public CompanyProfileService(CompanyRepository companyRepository) {
		this.companyRepository = companyRepository;
	}

	@Transactional(readOnly = true)
	public Company getByUserId(UUID userId) {
		return companyRepository.findByUserId(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMPANY_PROFILE_NOT_FOUND",
						"Perfil de empresa ainda não preenchido."));
	}

	/** Cria o perfil no primeiro envio e o atualiza nos seguintes. A dona é sempre a conta autenticada. */
	@Transactional
	public Company saveOwnProfile(UUID userId, UpdateCompanyProfileRequest request) {
		Company company = companyRepository.findByUserId(userId).orElseGet(() -> new Company(userId));
		apply(company, request);
		return companyRepository.save(company);
	}

	@Transactional(readOnly = true)
	public Company getById(UUID companyId) {
		return companyRepository.findById(companyId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMPANY_NOT_FOUND",
						"Empresa não encontrada."));
	}

	/** Atualização por id: uso exclusivo de intervenções administrativas, que auditam a operação. */
	@Transactional
	public Company updateById(UUID companyId, UpdateCompanyProfileRequest request) {
		Company company = getById(companyId);
		apply(company, request);
		return company;
	}

	private static void apply(Company company, UpdateCompanyProfileRequest request) {
		company.updateProfile(request.name(), request.sector(), request.description(), request.location());
	}
}

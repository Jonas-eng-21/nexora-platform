package br.com.nexora.companies.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.nexora.companies.entity.Company;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

	Optional<Company> findByUserId(UUID userId);
}

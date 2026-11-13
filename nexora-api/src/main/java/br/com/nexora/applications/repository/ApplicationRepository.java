package br.com.nexora.applications.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.nexora.applications.entity.Application;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

	boolean existsByStudentIdAndJobId(UUID studentId, UUID jobId);
}

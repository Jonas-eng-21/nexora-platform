package br.com.nexora.jobs.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.nexora.jobs.entity.Job;

public interface JobRepository extends JpaRepository<Job, UUID> {
}


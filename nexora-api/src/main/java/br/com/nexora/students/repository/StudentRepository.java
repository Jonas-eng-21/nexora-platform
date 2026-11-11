package br.com.nexora.students.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.nexora.students.entity.Student;

public interface StudentRepository extends JpaRepository<Student, UUID> {

	Optional<Student> findByUserId(UUID userId);
}

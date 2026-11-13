package br.com.nexora.students.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.nexora.students.entity.Student;

public interface StudentRepository extends JpaRepository<Student, UUID> {

	Optional<Student> findByUserId(UUID userId);

	/** Perfis dos ids informados cuja conta está ACTIVE. A ordem e o limite vêm do {@code Pageable}. */
	@Query("""
			select s from Student s
			where s.id in :ids
			  and exists (select 1 from User u where u.id = s.userId
			              and u.status = br.com.nexora.users.entity.AccountStatus.ACTIVE)
			""")
	List<Student> findActiveByIdIn(@Param("ids") Collection<UUID> ids, Pageable pageable);
}

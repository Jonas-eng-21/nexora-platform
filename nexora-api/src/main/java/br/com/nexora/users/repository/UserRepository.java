package br.com.nexora.users.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.nexora.users.entity.User;

public interface UserRepository extends JpaRepository<User, UUID> {
}

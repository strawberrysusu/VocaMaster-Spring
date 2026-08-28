package com.vocamaster.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    // JwtAuthFilter의 탈퇴 즉시 차단용 — PK 인덱스 존재 확인 1회 (Codex 검산 2026-08-28)
    boolean existsByIdAndDeletedAtIsNull(Long id);
}

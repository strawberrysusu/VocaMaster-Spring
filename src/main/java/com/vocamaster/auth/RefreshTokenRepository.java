package com.vocamaster.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * 해시로 토큰 row 조회 (폐기 여부 무관).
     * Reuse detection 시 "row가 존재하는지 + revoked 됐는지" 판별에 사용.
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Atomic UPDATE for rotation (CAS).
     * affected rows = 1  → 회전 성공 (이 호출이 race 우승)
     * affected rows = 0  → 토큰 없음 또는 이미 폐기됨 → 추가 SELECT로 reuse 판별
     *
     * flushAutomatically: 호출 직전 영속성 컨텍스트의 미반영 변경을 DB에 flush
     *                     (방금 save한 RefreshToken row가 UPDATE 대상이 되도록)
     * clearAutomatically: 호출 후 영속성 컨텍스트 clear → 후속 SELECT가 DB에서 재조회
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshToken r " +
           "SET r.revokedAt = :now, r.lastUsedIp = :ip " +
           "WHERE r.tokenHash = :hash AND r.revokedAt IS NULL")
    int revokeIfActive(@Param("hash") String hash,
                       @Param("now") LocalDateTime now,
                       @Param("ip") String ip);

    /**
     * Mass logout — 사용자의 모든 활성 refresh token 폐기.
     * 사용처: 비밀번호 변경 / reuse detection 발생 시.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now " +
           "WHERE r.user.id = :userId AND r.revokedAt IS NULL")
    int revokeAllByUserId(@Param("userId") Long userId,
                          @Param("now") LocalDateTime now);
}

package com.vocamaster.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

// @DynamicUpdate: 바뀐 컬럼만 UPDATE (Deck과 같은 처방). 없으면 닉네임 저장이 '읽었을 때의 password·deleted_at'까지 같이 써서
// 그 사이 커밋된 비밀번호 변경이 옛 비밀번호로, 탈퇴가 탈퇴 전으로 되돌아간다 (Codex 검산 2026-11-14, UserOverwriteTest)
@Entity
@DynamicUpdate
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    // 구글 가입자는 비밀번호가 없다 (V16, ADR-047) — null 계정의 이메일 로그인은 AuthService가 막는다
    @JsonIgnore
    private String password;

    @Column(nullable = false)
    private String nickname;

    // 가입 경로: local(이메일) | google — 통계·문의 대응용 기록
    @Builder.Default
    @Column(nullable = false)
    private String provider = "local";

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp private LocalDateTime updatedAt;
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // 학습 랭킹에서 닉네임 가리기 (V22, ADR-059) — 켜면 'qjw***'처럼 앞 몇 글자만
    @Builder.Default
    @Column(name = "ranking_masked", nullable = false)
    private boolean rankingMasked = false;

    public boolean isDeleted() {
        return deletedAt != null;
    }

}

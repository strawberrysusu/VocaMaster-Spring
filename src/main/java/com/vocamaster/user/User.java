package com.vocamaster.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
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

    public boolean isDeleted() {
        return deletedAt != null;
    }

}

package com.vocamaster.folder;

import com.vocamaster.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 📁 폴더 — 덱 분류 (V20, 동결 전 마지막 기능).
 * 덱 쪽은 연관 대신 folder_id 값 참조 — 폴더는 가벼운 라벨이라 LAZY 프록시·순환을 만들 이유가 없다.
 * 폴더 삭제 시 소속 덱의 folder_id는 DB가 SET NULL로 정리 (미분류 복귀).
 */
@Entity
@Table(name = "folders")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Folder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @CreationTimestamp
    private LocalDateTime createdAt;
}

package com.vocamaster.stats;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.vocamaster.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * 연속 학습일(Streak) 출석부 — 유저-날짜 쌍마다 1행.
 *
 * - streak: 행 생성 시점에 "어제 행 존재 여부"로 계산해 저장 (어제 있음 → 어제 streak+1, 없음 → 1)
 * - studyCount: 그날 학습 활동 횟수. 같은 날 두 번째부터는 이 값만 원자적 UPDATE로 +1
 * - @Version을 일부러 안 붙임: 통계 행 충돌 때문에 본 답변 트랜잭션까지 409로 죽는 것 방지.
 *   증가 분실은 원자적 UPDATE(incrementStudyCount)로 차단
 */
@Entity
@Table(name = "daily_user_stats",
        uniqueConstraints = @UniqueConstraint(name = "uq_daily_stats_user_date",
                columnNames = {"user_id", "stat_date"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class DailyUserStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "study_count", nullable = false)
    private int studyCount;

    @Column(nullable = false)
    private int streak;
}

package com.vocamaster.stats;

import com.vocamaster.common.exception.NotFoundException;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional
public class StatsService {

    // 정책이 "KST 자정 기준"이므로 서버 기본 시간대에 의존하지 않고 명시 (배포 서버가 UTC여도 동일 동작)
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DailyUserStatRepository dailyUserStatRepository;
    private final UserRepository userRepository;

    /**
     * 학습 활동 1회 = 출석 도장. 모든 학습 모드(Review/Quiz/Typing/Study)가 호출.
     * 호출한 쪽 트랜잭션에 합류하므로 답변 저장과 출석이 같이 성공하거나 같이 롤백된다.
     */
    public void recordStudy(Long userId) {
        LocalDate today = LocalDate.now(KST);

        // 오늘 줄이 이미 있으면 원자적 +1로 끝 (하루 중 두 번째부터 — 가장 흔한 경로, 쿼리 1번)
        int updated = dailyUserStatRepository.incrementStudyCount(userId, today);
        if (updated == 1) {
            return;
        }

        // 오늘 첫 학습 — 어제 줄을 보고 연속 여부 결정
        int streak = dailyUserStatRepository.findByUserIdAndStatDate(userId, today.minusDays(1))
                .map(yesterday -> yesterday.getStreak() + 1)    // 어제도 공부함 → 연속 +1
                .orElse(1);                                     // 끊김 → 1부터 다시

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("사용자를 찾을 수 없습니다"));

        // 알려진 한계: "오늘 첫 학습"이 정확히 동시에 2번 오면 UNIQUE 제약이 한쪽을 거부함 (희귀).
        // 데이터 오염은 제약이 막아주므로 지금은 단순한 코드를 유지 (개선 여지: 충돌 시 increment 재시도)
        dailyUserStatRepository.save(DailyUserStat.builder()
                .user(user)
                .statDate(today)
                .studyCount(1)
                .streak(streak)
                .build());
    }
}

package com.vocamaster.deck;

import com.vocamaster.study.event.StudyRecordedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 학습 이벤트 → 인기 점수 study 항 (ADR-038). StudyRecordedEvent의 두 번째 구독자.
 *
 * 정책 (Phase 4 ADR-033에서 보류했던 2가지 해소):
 *  ① 원본 귀속 — 복사본으로 공부해도 점수는 최상위 원본에 (original_deck_id는 복사 시점에 평탄화됨)
 *  ② 자기 학습 제외 — 학습자 == 귀속 덱 주인이면 0점 (상한 없는 자기 행동은 점수에서 뺀다, 자기 복사와 동일 기준)
 *  ③ 하루 1회 — (user, deck, date) unique 출석부. INSERT IGNORE 영향 행 수 1일 때만 +1
 *
 * 실행 시점/트랜잭션:
 *  - AFTER_COMMIT: 학습 트랜잭션이 확정된 뒤에만. 롤백이면 안 불림
 *  - REQUIRES_NEW: AFTER_COMMIT 시점엔 원래 트랜잭션이 끝나 있어 DB 쓰기엔 새 트랜잭션이 필요 (ADR-037 예고 함정)
 *  - 잠금 순서: 대상 덱 X 잠금(FOR UPDATE) → 출석부 INSERT → study_count UPDATE.
 *    INSERT 먼저 하면 FK 검사 S 잠금 뒤 X 승급 = 복사 API에서 실제로 겪은 데드락 재현 (ADR-031)
 *  - Redis ZSET +1은 이 새 트랜잭션의 커밋 후(rankingService.onStudied → afterCommit). 롤백 시 Redis만 +1 남는 일 없음
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeckStudyRankingListener {

    private final DeckRepository deckRepository;
    private final DeckStudyDayRepository studyDayRepository;
    private final DeckRankingService rankingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStudyRecorded(StudyRecordedEvent event) {
        if (event.deckId() == null) return;

        Deck studied = deckRepository.findById(event.deckId()).orElse(null);
        if (studied == null) return;                                   // 학습 직후 삭제된 덱 — 점수 줄 곳 없음

        // 원본이 삭제되면 FK가 SET NULL → originalDeckId가 null이 되어 복사본 자신이 대상이 됨 (의도)
        Long targetId = studied.getOriginalDeckId() != null ? studied.getOriginalDeckId() : studied.getId();

        Deck target = deckRepository.findWithLockById(targetId).orElse(null);   // ★ X 잠금 먼저
        if (target == null) return;
        if (target.getUser().getId().equals(event.userId())) return;           // 자기 학습 — 0점

        int inserted = studyDayRepository.insertIgnore(event.userId(), targetId, event.date());
        if (inserted == 0) return;                                             // 오늘 이미 셌음

        deckRepository.incrementStudyCount(targetId);
        rankingService.onStudied(target);                                      // 이 트랜잭션 커밋 후 ZSET +1 — target이 PUBLIC일 때만
    }
}

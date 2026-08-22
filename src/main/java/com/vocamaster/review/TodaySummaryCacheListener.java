package com.vocamaster.review;

import com.vocamaster.study.event.StudyRecordedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 학습 이벤트 → 요약 캐시 무효화. 출석(Stats)이 캐시(Review)를 직접 알던 결합을 끊은 자리.
 *
 * AFTER_COMMIT: 커밋 '확정 후'에만 듣는다. 트랜잭션 안에서 즉시 지우면
 * 커밋 전의 낡은 DB 값을 다른 요청이 읽어 다시 캐싱하는 빈틈이 생긴다 (ADR-035/036과 같은 이유).
 * 롤백되면 이 리스너는 아예 안 불린다 — 지울 이유가 없으니 맞는 동작.
 *
 * 지금은 동기(커밋한 스레드가 이어서 실행). 비동기(@Async)는 리스너가 무거워질 때 — ADR-037.
 */
@Component
@RequiredArgsConstructor
public class TodaySummaryCacheListener {

    private final TodaySummaryCache summaryCache;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStudyRecorded(StudyRecordedEvent event) {
        summaryCache.evict(event.userId(), event.date());
    }
}

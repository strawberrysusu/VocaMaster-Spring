package com.vocamaster.review;

import com.vocamaster.config.AsyncConfig;
import com.vocamaster.study.event.StudyRecordedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
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
 * @Async (ADR-039): 캐시 삭제는 '복구 가능한 복사본 작업'이라 비동기로 보내 응답에서 뗀다 —
 * 유실돼도 TTL 5분이 상한. 반대로 DeckStudyRankingListener의 출석부 INSERT는 원본 기록이라 동기 유지.
 */
@Component
@RequiredArgsConstructor
public class TodaySummaryCacheListener {

    private final TodaySummaryCache summaryCache;

    @Async(AsyncConfig.EVENT_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStudyRecorded(StudyRecordedEvent event) {
        summaryCache.evict(event.userId(), event.date());
    }
}

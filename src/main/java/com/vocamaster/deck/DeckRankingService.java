package com.vocamaster.deck;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 인기 덱 랭킹 — Redis Sorted Set (ADR-035).
 *
 * 제1원칙: **캐시는 id와 순서만 준다. 내용과 공개 권한의 최종 판단은 항상 DB.**
 * → 캐시가 낡아도 비공개 덱이 노출되는 사고는 구조적으로 불가능 (목록이 잠깐 성길 뿐).
 *
 * 일관성 전략 (둘의 조합):
 * - 이벤트 갱신: 좋아요/복사/공개전환이 '커밋 확정 후'(afterCommit) 점수 반영 — 즉시성
 * - TTL 재구축: 놓친 갱신이 있어도 최대 1시간 뒤 DB 기준으로 다시 수렴 — 최종적 일관성
 *
 * ready 표지 + TTL 시차(main 65분 > ready 60분):
 * ready가 항상 '먼저' 만료돼 증감이 멈춘 뒤에야 main이 사라진다. 이 시차가 없으면
 * 만료 직후 ZINCRBY가 "덱 하나짜리·TTL 없는 가짜 전체 순위표"를 만들 수 있다 (Codex 검산 ②).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeckRankingService {

    static final String KEY = "popular:decks";
    static final String READY_KEY = "popular:decks:ready";
    private static final Duration READY_TTL = Duration.ofMinutes(60);
    private static final Duration MAIN_TTL = Duration.ofMinutes(65);
    // ★ DeckRepository.searchByVisibilityPopular의 JPQL 가중치와 반드시 일치 (ADR-038)
    private static final int LIKE_WEIGHT = 5;
    private static final int COPY_WEIGHT = 3;
    private static final int STUDY_WEIGHT = 1;   // 보조 신호 — "실제로 쓰이는 덱", 하루 1점이라 낮게

    private final StringRedisTemplate redis;
    private final DeckRepository deckRepository;

    @Value("${ranking.popular.enabled:true}")
    private boolean enabled;

    /** 인기순 덱 id 한 페이지. null = 캐시 사용 불가 → 호출자가 DB 정렬로 (fail-open) */
    public List<Long> topDeckIds(int page, int size) {
        if (!enabled) return null;
        try {
            if (Boolean.FALSE.equals(redis.hasKey(READY_KEY))) {
                rebuild();
            }
            long start = (long) page * size;
            Set<String> ids = redis.opsForZSet().reverseRange(KEY, start, start + size - 1);
            if (ids == null) return null;
            List<Long> parsed = new ArrayList<>(ids.size());
            for (String raw : ids) {
                try {
                    parsed.add(Long.valueOf(raw));
                } catch (NumberFormatException e) {
                    // 숫자 아닌 멤버가 섞임 = 캐시 손상. NumberFormatException은 DataAccessException이 아니라
                    // 아래 catch를 뚫고 500이 되던 fail-open 구멍 (Codex 감사) → 자가 치유(삭제 → 다음 조회가 재구축) 후 DB로
                    log.warn("랭킹 캐시 손상(멤버 '{}') — 키 삭제 후 DB 정렬로 대체 (fail-open)", raw);
                    redis.delete(KEY);
                    redis.delete(READY_KEY);
                    return null;
                }
            }
            return parsed;
        } catch (DataAccessException e) {
            log.warn("랭킹 캐시 조회 실패 — DB 정렬로 대체 (fail-open)", e);
            return null;
        }
    }

    // === 도메인 이벤트 훅 — 전부 '커밋 확정 후' 실행, 롤백이면 실행 자체가 안 됨 (드리프트 방지) ===

    // ★ 훅은 Deck을 받아 PUBLIC일 때만 증분 (Codex 검산 2026-08-22): ZINCRBY는 없는 멤버를 '만들기' 때문에
    //   UNLISTED 좋아요·비공개 원본 study 점수가 순위표에 멤버로 섞인다. 노출은 DB 필터가 막지만 순위표가 더러워짐.
    //   DB 카운터(like/copy/study_count)는 visibility와 무관하게 사실로 쌓이고, Redis 사본만 PUBLIC으로 제한
    public void onLiked(Deck deck)   { incrementIfPublic(deck, LIKE_WEIGHT); }

    public void onUnliked(Deck deck) { incrementIfPublic(deck, -LIKE_WEIGHT); }

    public void onCopied(Deck deck)  { incrementIfPublic(deck, COPY_WEIGHT); }
    // 호출처가 REQUIRES_NEW 트랜잭션(DeckStudyRankingListener) 안이라, 그 새 트랜잭션의 커밋 후에 실행됨
    public void onStudied(Deck deck) { incrementIfPublic(deck, STUDY_WEIGHT); }

    private void incrementIfPublic(Deck deck, double delta) {
        if (deck.getVisibility() != DeckVisibility.PUBLIC) return;
        Long id = deck.getId();
        afterCommit(() -> incrementIfReady(id, delta));
    }

    /** 점수 공식의 자바 쪽 단일 지점 — 재구축·공개 전환이 모두 이걸 쓴다 (JPQL 쪽과 ★ 교차 참조) */
    static double score(Deck d) {
        return d.getLikeCount() * LIKE_WEIGHT + d.getCopyCount() * COPY_WEIGHT + d.getStudyCount() * STUDY_WEIGHT
                + tieBreaker(d.getId());
    }

    /**
     * 동점 정렬을 DB(`createdAt desc, id desc`)와 일치시키는 소수부 (Codex 감사 2026-08-25).
     * ZSET 동점은 멤버 문자열 사전순이라 "9"가 "10"보다 뒤로 가는 등 DB와 어긋났다.
     * id는 AUTO_INCREMENT라 'id 큰 쪽 = 최신' — id/1e12를 점수에 실으면 동점에서 최신이 위.
     * 정수 가중치 증분(ZINCRBY ±5·±3·±1)은 소수부를 건드리지 않아 기존 훅은 무변경.
     * (한계: 재구축·공개 전환을 안 거친 멤버가 증분만으로 생기면 소수부가 없다 — PUBLIC 가드상 정상 경로에선 없음)
     */
    private static double tieBreaker(long id) {
        return id / 1e12;
    }

    /** PRIVATE/UNLISTED → PUBLIC 전환: 현재 DB 카운트 기준 점수로 등재.
     *  예전엔 like·copy만 받아 study 항이 빠졌었다 — 비공개 상태에서 쌓인 study_count가 공개 직후 순위에 누락 (Codex 검산) */
    public void onBecamePublic(Deck deck) {
        Long deckId = deck.getId();
        double score = score(deck);
        afterCommit(() -> {
            try {
                if (Boolean.TRUE.equals(redis.hasKey(READY_KEY))) {
                    redis.opsForZSet().add(KEY, String.valueOf(deckId), score);
                }
            } catch (DataAccessException e) {
                log.warn("랭킹 등재 실패 — TTL 재구축이 수습 (fail-open)", e);
            }
        });
    }

    /** PUBLIC 이탈(비공개 전환·삭제): 제거. 없는 멤버의 ZREM은 무해라 ready 가드 불필요 */
    public void onLeftPublic(Long deckId) {
        afterCommit(() -> {
            try {
                redis.opsForZSet().remove(KEY, String.valueOf(deckId));
            } catch (DataAccessException e) {
                log.warn("랭킹 제거 실패 — DB 필터가 노출은 막고, TTL 재구축이 수습 (fail-open)", e);
            }
        });
    }

    /** 조회 중 발견한 낡은 id 청소 (자가 치유 — Codex 검산 ⑤) */
    public void evictStale(Collection<Long> deckIds) {
        try {
            Object[] members = deckIds.stream().map(String::valueOf).toArray();
            redis.opsForZSet().remove(KEY, members);
            log.info("랭킹 stale id {}건 청소", deckIds.size());
        } catch (DataAccessException e) {
            log.warn("stale 청소 실패 — TTL 재구축이 수습", e);
        }
    }

    /** 완성된 캐시(ready)가 있을 때만 증감 — 만료 후 증감이 가짜 순위표를 만드는 것 방지 */
    private void incrementIfReady(Long deckId, double delta) {
        try {
            if (Boolean.TRUE.equals(redis.hasKey(READY_KEY))) {
                redis.opsForZSet().incrementScore(KEY, String.valueOf(deckId), delta);
            }
        } catch (DataAccessException e) {
            log.warn("랭킹 점수 반영 실패 — TTL 재구축이 수습 (fail-open)", e);
        }
    }

    // 전체 재구축: DEL → PUBLIC 전체 ZADD → main TTL(65m) → ready 표지(60m).
    // 동시에 두 요청이 재구축하면 마지막이 이김 — 같은 DB를 읽으므로 결과 동일 (멱등)
    private void rebuild() {
        List<Deck> publicDecks = deckRepository.findByVisibilityAndUser_DeletedAtIsNull(DeckVisibility.PUBLIC);
        redis.delete(KEY);
        if (!publicDecks.isEmpty()) {
            Set<ZSetOperations.TypedTuple<String>> tuples = publicDecks.stream()
                    .map(d -> ZSetOperations.TypedTuple.of(
                            String.valueOf(d.getId()),
                            score(d)))
                    .collect(Collectors.toSet());
            redis.opsForZSet().add(KEY, tuples);
            redis.expire(KEY, MAIN_TTL);
        }
        redis.opsForValue().set(READY_KEY, "1", READY_TTL);
        log.info("인기 랭킹 재구축 — PUBLIC 덱 {}개", publicDecks.size());
    }

    // 트랜잭션 안이면 커밋 확정 후 실행(롤백 시 실행 안 됨), 밖이면 즉시.
    // afterCommit 안의 예외는 각 action이 자체 삼킴 — DB는 이미 성공했는데 Redis 때문에
    // 500이 나가면 사용자가 재시도해 중복(복사 2번 등)이 생긴다 (Codex 검산 ④)
    private void afterCommit(Runnable action) {
        if (!enabled) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}

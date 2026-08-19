package com.vocamaster.deck.dto;

import com.vocamaster.deck.Deck;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 공개 API 전용 응답 (ADR-030).
 * 내부용 DeckResponse와 분리 — 공개 표면은 노출 필드를 별도 계약으로 통제.
 * 작성자는 email이 아니라 닉네임만.
 */
@Getter
@Builder
public class PublicDeckResponse {

    private Long id;
    private String title;
    private String description;
    private String authorNickname;
    private long cardCount;
    private long likeCount;      // 인기 정렬 근거를 응답에서 확인 가능하게 (ADR-033)
    private long copyCount;
    private boolean likedByMe;   // 로그인 사용자 기준 — 익명은 false. 탐색 ♥ 초기 상태용
    private boolean mine;        // 내 덱이면 true — 자기 복사는 copy_count에 안 오르므로 UI가 +1 하지 않게
    private LocalDateTime createdAt;

    public static PublicDeckResponse from(Deck deck, long cardCount) {
        return from(deck, cardCount, false, false);
    }

    public static PublicDeckResponse from(Deck deck, long cardCount, boolean likedByMe, boolean mine) {
        return PublicDeckResponse.builder()
                .id(deck.getId())
                .title(deck.getTitle())
                .description(deck.getDescription() != null ? deck.getDescription() : "")
                .authorNickname(deck.getUser().getNickname())
                .cardCount(cardCount)
                .likeCount(deck.getLikeCount())
                .copyCount(deck.getCopyCount())
                .likedByMe(likedByMe)
                .mine(mine)
                .createdAt(deck.getCreatedAt())
                .build();
    }
}

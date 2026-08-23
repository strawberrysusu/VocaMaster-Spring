package com.vocamaster.card.dto;

import com.vocamaster.card.Card;
import lombok.Builder;
import lombok.Getter;

/**
 * 공개 API 전용 카드 응답 — 화이트리스트 (ADR-030 원칙: 공개 표면은 노출 필드를 별도 계약으로).
 * 내부용 CardResponse의 starred(개인 학습 상태)·createdAt/updatedAt은 의도적으로 제외.
 * 복사 시 starred를 리셋하는 정책과 일관되게, 공개 표면에서도 개인 상태는 보이지 않는다.
 */
@Getter
@Builder
public class PublicCardResponse {

    private Long id;
    private String front;
    private String back;
    private String reading;     // 읽기(요미가나), 없으면 null
    private String exampleSentence;
    private Integer position;

    public static PublicCardResponse from(Card card) {
        return PublicCardResponse.builder()
                .id(card.getId())
                .front(card.getFront())
                .back(card.getBack())
                .reading(card.getReading())
                .exampleSentence(card.getExampleSentence())
                .position(card.getPosition())
                .build();
    }
}

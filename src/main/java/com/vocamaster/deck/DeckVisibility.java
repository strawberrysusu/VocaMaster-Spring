package com.vocamaster.deck;

/**
 * 덱 공개 범위 (ADR-030)
 * - PRIVATE:  소유자만 조회 (기본값)
 * - PUBLIC:   공개 검색에 노출, 누구나 조회
 * - UNLISTED: 검색 비노출, 링크를 아는 사람만 조회
 */
public enum DeckVisibility {
    PRIVATE, PUBLIC, UNLISTED
}

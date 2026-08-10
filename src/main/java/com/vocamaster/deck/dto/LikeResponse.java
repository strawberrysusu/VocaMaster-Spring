package com.vocamaster.deck.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LikeResponse {

    private final boolean liked;     // 이 요청 후 내 좋아요 상태
    private final long likeCount;    // 이 요청 후 덱의 총 좋아요 수

    public static LikeResponse of(boolean liked, long likeCount) {
        return new LikeResponse(liked, likeCount);
    }
}

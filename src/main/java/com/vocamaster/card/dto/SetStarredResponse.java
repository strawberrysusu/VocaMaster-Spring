package com.vocamaster.card.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class SetStarredResponse {

    /** 요청한 값 */
    private final boolean starred;

    /**
     * 이번 요청으로 값이 실제로 바뀐 카드 id (오름차순). 이미 그 값이던 카드는 빠진다 —
     * 온라인 시험지는 이걸로 "이 시험이 새로 켠 별표"만 기억했다가 '맞음으로' 때 그것만 끈다.
     */
    private final List<Long> changedIds;

    /**
     * 건너뛴 카드 id (오름차순) — 이 덱에 없는 카드: 시험 도중 지워졌거나, 처음부터 이 덱 카드가 아니다.
     * 값을 바꾸지 않는다 (11/14 검토: 예전엔 하나라도 섞이면 400이라 그 시험의 별표가 전부 실패했다).
     */
    private final List<Long> skippedIds;
}

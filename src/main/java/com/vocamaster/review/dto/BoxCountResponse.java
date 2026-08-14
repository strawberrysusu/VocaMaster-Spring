package com.vocamaster.review.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 라이트너 박스 한 칸의 카드 수 — 홈 '박스 사다리' 차트용 (목업이 요구해서 신설된 첫 API) */
@Getter
@AllArgsConstructor
public class BoxCountResponse {

    private final int box;      // 1~6
    private final long count;
}

package com.vocamaster.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * 페이지네이션 안전장치.
 * - page < 0  → 0으로 보정
 * - size < 1  → 1로 보정
 * - size > 100 → 100으로 캡 (서버 부하 방지)
 *
 * 클라이언트가 size=999999 같은 거 보내도 OOM/장기 쿼리로 안 빠짐.
 */
public final class PageableUtils {

    private static final int MIN_SIZE = 1;
    private static final int MAX_SIZE = 100;

    private PageableUtils() {}

    public static PageRequest safe(int page, int size, Sort sort) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(MIN_SIZE, Math.min(MAX_SIZE, size));
        return PageRequest.of(safePage, safeSize, sort);
    }
}

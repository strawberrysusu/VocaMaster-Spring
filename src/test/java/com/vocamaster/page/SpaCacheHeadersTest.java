package com.vocamaster.page;

import com.vocamaster.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /app 정적 응답의 헤더 고정 (2026-11-09 운영 실측에서 출발).
 *
 * 실측: 해시 이름 번들(index-G2xNdP-Y.js, 339KB)까지 Spring Security 기본값 no-store라 방문마다 다시 받았다.
 * 수리: /app/assets/**만 1년 immutable, 입구(index.html·딥링크)는 지금처럼 매번 새로 받기.
 *
 * 요청은 secure(true)로 보낸다 — HSTS는 https 요청에만 붙기 때문이다.
 * 운영에서는 nginx가 보낸 X-Forwarded-Proto: https를 ForwardedHeaderFilter(forward-headers-strategy: framework)가
 * 읽어서 같은 상태가 된다. 테스트용 application.yml에는 그 설정이 없어서 secure(true)로 직접 만든다.
 *
 * 파일은 src/test/resources/static/app의 고정 이름 가짜 번들을 쓴다 (진짜 번들은 해시 이름이 빌드마다 바뀜).
 */
@AutoConfigureMockMvc
class SpaCacheHeadersTest extends AbstractIntegrationTest {

    /** Spring Security 기본값(CacheControlHeadersWriter) — '저장하지 말고 매번 서버에서 받아라' */
    private static final String SECURITY_DEFAULT_NO_STORE = "no-cache, no-store, max-age=0, must-revalidate";

    @Autowired private MockMvc mvc;

    @Test
    @DisplayName("해시 이름 자산 = 1년 immutable 한 벌 — Spring Security가 no-store를 덧붙이지 않는다")
    void hashedAsset_cachedForOneYear() throws Exception {
        mvc.perform(get("/app/assets/cache-probe.js").secure(true))
                .andExpect(status().isOk())
                // stringValues = 값 목록 전체 비교 → Cache-Control이 정확히 한 줄인지까지 고정
                .andExpect(header().stringValues(HttpHeaders.CACHE_CONTROL, "max-age=31536000, public, immutable"))
                // CacheControlHeadersWriter가 건너뛴 증거 — 썼다면 Pragma: no-cache, Expires: 0도 같이 붙는다
                .andExpect(header().doesNotExist(HttpHeaders.PRAGMA))
                .andExpect(header().doesNotExist(HttpHeaders.EXPIRES))
                .andExpect(appOwnedSecurityHeaders());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/app/index.html", "/app/decks/6"})   // 입구 파일, 딥링크 fallback
    @DisplayName("입구(index.html)와 딥링크 = 매번 새로 받기 — 새 배포의 새 해시 이름을 알려 주는 곳")
    void entry_isNeverStored(String path) throws Exception {
        mvc.perform(get(path).secure(true))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().stringValues(HttpHeaders.CACHE_CONTROL, SECURITY_DEFAULT_NO_STORE))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(header().string(HttpHeaders.EXPIRES, "0"))
                .andExpect(appOwnedSecurityHeaders());
    }

    @Test
    @DisplayName("없는 자산 = 404 — index.html로 fallback해서 자산 URL에 1년 캐시되는 사고 방지")
    void missingAsset_is404_notIndexFallback() throws Exception {
        // 배포 직후 옛 index.html이 가리키던 해시 같은 것
        mvc.perform(get("/app/assets/index-OLDHASH.js"))
                .andExpect(status().isNotFound())
                .andExpect(header().stringValues(HttpHeaders.CACHE_CONTROL, SECURITY_DEFAULT_NO_STORE));
    }

    /**
     * nginx와 중복되던 보안 헤더 3종 — nginx 쪽을 지우면 운영에서는 앱이 붙인 이 한 벌만 남는다.
     * 그래서 앱에서 빠지면 운영 응답에서 통째로 사라진다 → 여기서 값과 '한 줄'을 고정.
     */
    private static ResultMatcher appOwnedSecurityHeaders() {
        return result -> {
            header().stringValues("X-Content-Type-Options", "nosniff").match(result);
            header().stringValues("X-Frame-Options", "DENY").match(result);
            header().stringValues("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains").match(result);
        };
    }

    @Test
    @DisplayName("루트의 사이트 아이콘(/favicon.svg·/favicon.ico)은 401이 아니라 앱 아이콘으로 돌려보낸다 (Codex 검산 11/14)")
    void rootFavicon_redirectsToAppIcon() throws Exception {
        for (String path : new String[] {"/favicon.svg", "/favicon.ico"}) {
            mvc.perform(get(path).secure(true))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/app/favicon.svg"));
        }
    }
}

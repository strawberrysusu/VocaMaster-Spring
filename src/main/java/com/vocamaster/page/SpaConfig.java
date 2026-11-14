package com.vocamaster.page;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.time.Duration;

/**
 * React SPA 서빙 (/app/**).
 *
 * 딥링크 fallback: /app/decks/6을 새로고침하면 그런 정적 파일은 없다 —
 * 실제 파일이 없는 요청은 전부 index.html로 돌려서 라우팅을 React가 이어받게 한다.
 * (컨트롤러 forward 방식은 index.html 요청이 다시 자기 매핑에 걸리는 순환 위험이 있어
 *  리소스 리졸버 fallback이 정석)
 *
 * 캐시 (2026-11-09 운영 실측: 해시 이름 번들 339KB까지 no-store라 방문할 때마다 다시 받았음):
 * - /app/assets/** = 1년 + immutable. Vite가 파일 내용의 해시를 이름에 넣는다(index-G2xNdP-Y.js).
 *   내용이 바뀌면 이름(URL)이 바뀌므로, 같은 URL은 영원히 같은 내용 → 다시 물어볼 필요가 없다.
 * - 그 밖의 /app/** (index.html·딥링크·favicon) = Cache-Control을 정하지 않는다 →
 *   Spring Security 기본값(no-cache, no-store, max-age=0, must-revalidate)이 붙어 매번 새로 받는다.
 *   새 배포의 새 해시 이름은 index.html이 알려 주므로 입구는 캐시되면 안 된다.
 * Spring Security의 CacheControlHeadersWriter는 응답에 Cache-Control이 이미 있으면 아무것도 안 쓴다
 * (spring-security-web 6.5.11 바이트코드로 확인, SpaCacheHeadersTest가 고정).
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    /**
     * 루트의 사이트 아이콘 — 앱 화면은 /app/favicon.svg를 쓰지만, 브라우저·검사 도구가 루트(/favicon.svg·/favicon.ico)를
     * 직접 부르면 인증 필터에 걸려 401 JSON이 나가고 콘솔 오류가 남았다 (Codex 검산 11/14, Edge 검사).
     * 같은 파일로 돌려보낸다 — 두 경로는 보안 설정(SecurityConfig)에서 열어 둔다
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/favicon.svg", "/app/favicon.svg");
        registry.addRedirectViewController("/favicon.ico", "/app/favicon.svg");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 더 구체적인 패턴이 이긴다 — /app/assets/x.js는 아래 /app/** 대신 이 핸들러로 온다.
        // index.html fallback을 일부러 안 붙인다: 없는 자산은 404.
        // fallback이 있으면 배포 직후 옛 해시 요청에 index.html이 나가고, 그게 자산 URL로 1년 캐시된다.
        // 주의: frontend/public/assets/를 만들면 해시 없는 파일이 이 경로로 복사돼 1년 고정된다 — 만들지 말 것
        registry.addResourceHandler("/app/assets/**")
                .addResourceLocations("classpath:/static/app/assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .resourceChain(true);

        registry.addResourceHandler("/app/**")
                .addResourceLocations("classpath:/static/app/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        // 빈 경로("/app/")는 디렉토리 리소스로 해석돼 읽기에서 500이 났었음(2026-08-14 실측)
                        // — 실제 '파일' 요청만 그대로 서빙하고 나머지는 전부 index.html로
                        if (resourcePath != null && !resourcePath.isEmpty()) {
                            Resource requested = location.createRelative(resourcePath);
                            if (requested.exists() && requested.isReadable()) {
                                return requested;
                            }
                        }
                        Resource index = new ClassPathResource("/static/app/index.html");
                        return index.exists() ? index : null;   // 번들 없는 환경이면 404 (500 아님)
                    }
                });
    }

    @Controller
    static class RootRedirect {
        @GetMapping("/")
        public String root() {
            return "redirect:/app/";        // 새 화면이 기본 입구, 옛 화면은 /pages/** 에 유지
        }

        // 빈 경로("/app/")는 ResourceHttpRequestHandler가 리졸버 호출 '전에'
        // NoResourceFoundException을 던짐 (실측 500) — 실제 파일 경로로 forward해서 우회
        @GetMapping({"/app", "/app/"})
        public String appIndex() {
            return "forward:/app/index.html";
        }
    }
}

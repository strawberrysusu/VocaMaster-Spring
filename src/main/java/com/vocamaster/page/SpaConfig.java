package com.vocamaster.page;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * React SPA 서빙 (/app/**).
 *
 * 딥링크 fallback: /app/decks/6을 새로고침하면 그런 정적 파일은 없다 —
 * 실제 파일이 없는 요청은 전부 index.html로 돌려서 라우팅을 React가 이어받게 한다.
 * (컨트롤러 forward 방식은 index.html 요청이 다시 자기 매핑에 걸리는 순환 위험이 있어
 *  리소스 리졸버 fallback이 정석)
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
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

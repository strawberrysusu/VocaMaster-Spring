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
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        return new ClassPathResource("/static/app/index.html");
                    }
                });
    }

    @Controller
    static class RootRedirect {
        @GetMapping("/")
        public String root() {
            return "redirect:/app/";        // 새 화면이 기본 입구, 옛 화면은 /pages/** 에 유지
        }
    }
}

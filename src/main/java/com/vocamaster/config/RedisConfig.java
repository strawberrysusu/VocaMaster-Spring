package com.vocamaster.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 설정 (Phase 5).
 *
 * - 커넥션은 Lettuce (starter 기본) — 논블로킹 + 스레드 세이프라 커넥션 하나를 공유
 * - 단순 카운터(rate limit)는 자동 제공되는 StringRedisTemplate을 그대로 사용
 * - 객체 캐시(랭킹/요약)는 아래 JSON 템플릿 사용 — redis-cli로 사람이 읽을 수 있는 형식
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 키는 평문 문자열 — 안 그러면 redis-cli에서 키가 깨져 보여 운영 중 디버깅이 불가능해짐
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(cacheObjectMapper());
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    private ObjectMapper cacheObjectMapper() {
        // 역직렬화 대상 타입을 우리 패키지/표준 타입으로 제한 — 임의 클래스 역직렬화(가젯 공격) 차단
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.vocamaster.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .build();

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());                        // LocalDateTime 등
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);     // 숫자 대신 ISO 문자열
        mapper.activateDefaultTyping(typeValidator,
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        return mapper;
    }
}

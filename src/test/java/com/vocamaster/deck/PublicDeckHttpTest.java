package com.vocamaster.deck;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 진짜 HTTP로 검증하는 것 (서비스 테스트로는 불가능한 층):
 * - SecurityConfig의 /public/** permitAll이 실제로 익명 요청을 통과시키는가
 * - 비공개 404와 없는 덱 404가 HTTP 응답에서도 구별 불가인가 (존재 숨김, ADR-030)
 *
 * AbstractIntegrationTest를 안 쓰는 이유: 랜덤 포트 서버는 별도 스레드/커넥션이라
 * 테스트 @Transactional의 미커밋 데이터가 안 보임 → 커밋 방식 + 수동 정리.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PublicDeckHttpTest {

    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.32")
            .withDatabaseName("vocamaster_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired private TestRestTemplate rest;
    @Autowired private UserRepository userRepository;
    @Autowired private DeckRepository deckRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private User user;
    private Deck pub;
    private Deck unlisted;
    private Deck priv;
    private String tag;   // 재사용 컨테이너의 잔여 데이터에 면역이 되도록 고유 태그

    @BeforeEach
    void setUp() {
        tag = "h" + System.nanoTime();
        user = userRepository.save(User.builder()
                .email(tag + "@test.com").password("encoded").nickname("httper").build());
        pub = deckRepository.save(Deck.builder()
                .title(tag + " 공개덱").visibility(DeckVisibility.PUBLIC).user(user).build());
        unlisted = deckRepository.save(Deck.builder()
                .title(tag + " 링크덱").visibility(DeckVisibility.UNLISTED).user(user).build());
        priv = deckRepository.save(Deck.builder()
                .title(tag + " 비밀덱").visibility(DeckVisibility.PRIVATE).user(user).build());
    }

    @AfterEach
    void tearDown() {
        deckRepository.delete(pub);
        deckRepository.delete(unlisted);
        deckRepository.delete(priv);
        userRepository.delete(user);
    }

    @Test
    @DisplayName("익명(무토큰) 검색 요청이 200 — permitAll 실검증")
    void anonymousSearch_ok() throws Exception {
        ResponseEntity<String> res = rest.getForEntity("/public/decks?keyword=" + tag, String.class);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        JsonNode body = objectMapper.readTree(res.getBody());
        assertEquals(1, body.get("totalElements").asInt());   // tag 스코프 안에선 PUBLIC 1개만
        assertEquals(tag + " 공개덱", body.get("content").get(0).get("title").asText());
    }

    @Test
    @DisplayName("익명으로 보호 API(/decks) 접근은 차단됨")
    void anonymousProtected_blocked() {
        ResponseEntity<String> res = rest.getForEntity("/decks", String.class);
        // entry point 미설정 → Spring Security 기본 403 (401 아님 — JwtAuthFilter 주석과 다른 실측값)
        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    @Test
    @DisplayName("익명 좋아요(POST /public/**)는 차단 — permitAll이 GET에만 열려있음 (ADR-032)")
    void anonymousLike_blocked() {
        ResponseEntity<String> res = rest.postForEntity(
                "/public/decks/" + pub.getId() + "/like", null, String.class);
        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
    }

    @Test
    @DisplayName("UNLISTED는 링크(직접 URL)로 200")
    void unlistedDetail_visible() {
        ResponseEntity<String> res = rest.getForEntity("/public/decks/" + unlisted.getId(), String.class);
        assertEquals(HttpStatus.OK, res.getStatusCode());
    }

    @Test
    @DisplayName("PRIVATE 404와 없는 덱 404는 HTTP에서도 구별 불가 (status/code/message 동일)")
    void privateDeck_indistinguishableFromMissing() throws Exception {
        ResponseEntity<String> privateRes = rest.getForEntity("/public/decks/" + priv.getId(), String.class);
        ResponseEntity<String> missingRes = rest.getForEntity("/public/decks/999999", String.class);

        assertEquals(HttpStatus.NOT_FOUND, privateRes.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, missingRes.getStatusCode());

        JsonNode p = objectMapper.readTree(privateRes.getBody());
        JsonNode m = objectMapper.readTree(missingRes.getBody());
        // timestamp는 모든 요청마다 달라 구별 신호가 아님 — 나머지 필드가 동일해야 함
        assertEquals(m.get("status"), p.get("status"));
        assertEquals(m.get("code"), p.get("code"));
        assertEquals(m.get("message"), p.get("message"));
    }
}

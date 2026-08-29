package com.vocamaster.user;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.auth.AuthService;
import com.vocamaster.auth.dto.RegisterRequest;
import com.vocamaster.auth.dto.TokenPair;
import com.vocamaster.common.exception.NotFoundException;
import com.vocamaster.deck.DeckLikeService;
import com.vocamaster.deck.DeckRepository;
import com.vocamaster.deck.DeckService;
import com.vocamaster.deck.DeckVisibility;
import com.vocamaster.deck.PublicDeckService;
import com.vocamaster.deck.dto.CreateDeckRequest;
import com.vocamaster.deck.dto.DeckResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 탈퇴 정책 ↔ 실제 동작 정합 (Codex 검산 2026-08-28).
 *
 * privacy.html의 약속("로그인 차단 + 공개 콘텐츠 노출 중단")을 코드가 지키는지:
 * ① 탈퇴 즉시 기존 access token(최대 1h 잔존)도 401 — JwtAuthFilter의 탈퇴 확인
 * ② 탈퇴자의 PUBLIC 덱은 검색·상세 모두에서 사라짐 — 쿼리 제외 + visibleDeckOrThrow
 */
@AutoConfigureMockMvc
class WithdrawalConsistencyTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private AuthService authService;
    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private DeckService deckService;
    @Autowired private DeckLikeService deckLikeService;
    @Autowired private DeckRepository deckRepository;
    @Autowired private PublicDeckService publicDeckService;

    private TokenPair register(String email) {
        RegisterRequest req = new RegisterRequest();
        req.setEmail(email);
        req.setPassword("Passw0rd!");
        req.setNickname("탈퇴테스트");
        return authService.register(req, "test-ua", "127.0.0.1");
    }

    @Test
    @DisplayName("탈퇴하면 아직 만료 전인 access token도 즉시 401")
    void withdrawnUser_existingAccessTokenRejected() throws Exception {
        String email = "wd_" + System.nanoTime() + "@test.com";
        TokenPair pair = register(email);
        Long userId = userRepository.findByEmail(email).orElseThrow().getId();

        mvc.perform(get("/users/me").header("Authorization", "Bearer " + pair.accessToken()))
                .andExpect(status().isOk());

        userService.deleteAccount(userId);

        mvc.perform(get("/users/me").header("Authorization", "Bearer " + pair.accessToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("탈퇴자의 PUBLIC 덱 — 공개 검색과 상세에서 모두 사라짐")
    void withdrawnUser_publicDecksHidden() {
        String email = "wdd_" + System.nanoTime() + "@test.com";
        register(email);
        Long userId = userRepository.findByEmail(email).orElseThrow().getId();

        CreateDeckRequest create = new CreateDeckRequest();
        create.setTitle("탈퇴자의 공개 덱");
        DeckResponse deck = deckService.create(userId, create);
        deckService.updateVisibility(deck.getId(), userId, DeckVisibility.PUBLIC);

        assertTrue(publicDeckService.search(null, 0, 50, "recent").getContent().stream()
                        .anyMatch(d -> d.getId().equals(deck.getId())),
                "탈퇴 전에는 검색에 보여야");
        assertDoesNotThrow(() -> publicDeckService.findOne(deck.getId()));

        userService.deleteAccount(userId);

        assertFalse(publicDeckService.search(null, 0, 50, "recent").getContent().stream()
                        .anyMatch(d -> d.getId().equals(deck.getId())),
                "탈퇴 후에는 검색에서 사라져야");
        assertThrows(NotFoundException.class, () -> publicDeckService.findOne(deck.getId()),
                "상세도 404 — 존재 자체를 숨김");
    }

    @Test
    @DisplayName("탈퇴자 덱 ID를 아는 사용자의 복사·좋아요 우회 — 검색·상세와 동일하게 404 (Codex 검산 8/29)")
    void withdrawnUser_copyAndLikeBypassBlocked() {
        String ownerEmail = "own_" + System.nanoTime() + "@test.com";
        String otherEmail = "atk_" + System.nanoTime() + "@test.com";
        register(ownerEmail);
        register(otherEmail);
        Long ownerId = userRepository.findByEmail(ownerEmail).orElseThrow().getId();
        Long otherId = userRepository.findByEmail(otherEmail).orElseThrow().getId();

        CreateDeckRequest create = new CreateDeckRequest();
        create.setTitle("탈퇴 전 공개 덱");
        DeckResponse deck = deckService.create(ownerId, create);
        deckService.updateVisibility(deck.getId(), ownerId, DeckVisibility.PUBLIC);

        // 탈퇴 전엔 복사·좋아요 정상
        assertDoesNotThrow(() -> deckLikeService.like(deck.getId(), otherId));
        assertDoesNotThrow(() -> deckLikeService.unlike(deck.getId(), otherId));

        userService.deleteAccount(ownerId);

        assertThrows(NotFoundException.class, () -> deckService.copy(deck.getId(), otherId),
                "탈퇴자 덱은 ID로 직접 복사해도 404");
        assertThrows(NotFoundException.class, () -> deckLikeService.like(deck.getId(), otherId),
                "좋아요도 동일 404");
        assertThrows(NotFoundException.class, () -> deckLikeService.unlike(deck.getId(), otherId),
                "좋아요 취소도 동일 404");
    }

    @Test
    @DisplayName("인기 캐시의 전체 개수·재구축 재료 — 탈퇴자 덱 제외 (유령 페이지·유령 ZSET 멤버 방지)")
    void withdrawnUser_excludedFromCountAndRebuildSource() {
        String email = "cnt_" + System.nanoTime() + "@test.com";
        register(email);
        Long userId = userRepository.findByEmail(email).orElseThrow().getId();

        CreateDeckRequest create = new CreateDeckRequest();
        create.setTitle("카운트 검증 덱");
        DeckResponse deck = deckService.create(userId, create);
        deckService.updateVisibility(deck.getId(), userId, DeckVisibility.PUBLIC);

        long before = deckRepository.countByVisibilityAndUser_DeletedAtIsNull(DeckVisibility.PUBLIC);
        assertTrue(deckRepository.findByVisibilityAndUser_DeletedAtIsNull(DeckVisibility.PUBLIC).stream()
                .anyMatch(d -> d.getId().equals(deck.getId())));

        userService.deleteAccount(userId);

        assertEquals(before - 1, deckRepository.countByVisibilityAndUser_DeletedAtIsNull(DeckVisibility.PUBLIC),
                "캐시 경로의 total(페이지 수 계산)에서 빠져야");
        assertFalse(deckRepository.findByVisibilityAndUser_DeletedAtIsNull(DeckVisibility.PUBLIC).stream()
                        .anyMatch(d -> d.getId().equals(deck.getId())),
                "랭킹 재구축 재료에서도 빠져야");
    }
}

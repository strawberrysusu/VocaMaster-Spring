package com.vocamaster.deck;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.card.Card;
import com.vocamaster.card.CardRepository;
import com.vocamaster.common.exception.BadRequestException;
import com.vocamaster.common.exception.NotFoundException;
import com.vocamaster.deck.dto.PublicDeckResponse;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PublicDeckServiceTest extends AbstractIntegrationTest {

    @Autowired private PublicDeckService publicDeckService;
    @Autowired private DeckLikeService deckLikeService;
    @Autowired private DeckRepository deckRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private UserRepository userRepository;

    private User deckOwner;
    private Deck pub;       // PUBLIC, 제목에 tag+토익
    private Deck pub2;      // PUBLIC, 설명에 tag+일본어
    private Deck unlisted;  // UNLISTED, 제목에 tag+토익 — 검색엔 절대 안 나와야 함
    private Deck priv;      // PRIVATE, 설명에 tag+토익 — And/Or 괄호 사고 나면 이게 새어 나옴

    // 검색어를 tag로 스코프 — 재사용 컨테이너에 남을 수 있는 다른 테스트의 커밋 데이터에 면역
    private String tag;

    @BeforeEach
    void setUp() {
        tag = "t" + System.nanoTime();
        User user = userRepository.save(User.builder()
                .email(tag + "@test.com")
                .password("encoded")
                .nickname("decker")
                .build());
        deckOwner = user;

        pub = deckRepository.save(Deck.builder()
                .title(tag + " 토익 영단어").description("기초부터")
                .visibility(DeckVisibility.PUBLIC).user(user).build());
        pub2 = deckRepository.save(Deck.builder()
                .title(tag + " JLPT N1").description(tag + " 일본어 상급 어휘")
                .visibility(DeckVisibility.PUBLIC).user(user).build());
        unlisted = deckRepository.save(Deck.builder()
                .title(tag + " 토익 스터디 전용").description("링크 공유용")
                .visibility(DeckVisibility.UNLISTED).user(user).build());
        priv = deckRepository.save(Deck.builder()
                .title("내 오답노트").description(tag + " 토익 틀린 것만")
                .visibility(DeckVisibility.PRIVATE).user(user).build());
    }

    @Test
    @DisplayName("검색 목록에는 PUBLIC만 — UNLISTED/PRIVATE 제외")
    void search_publicOnly() {
        Page<PublicDeckResponse> result = publicDeckService.search(tag, 0, 20, null);

        List<Long> ids = result.map(PublicDeckResponse::getId).getContent();
        assertEquals(2, result.getTotalElements());
        assertTrue(ids.contains(pub.getId()));
        assertTrue(ids.contains(pub2.getId()));
        assertFalse(ids.contains(unlisted.getId()));
        assertFalse(ids.contains(priv.getId()));
    }

    @Test
    @DisplayName("keyword 검색 — 괄호 사고 회귀: PRIVATE 설명 매치·UNLISTED 제목 매치가 새면 안 됨")
    void search_keyword_noLeak() {
        // tag+" 토익"은 priv의 설명, unlisted의 제목에도 있음 — PUBLIC인 pub만 나와야 함
        Page<PublicDeckResponse> result = publicDeckService.search(tag + " 토익", 0, 20, null);

        assertEquals(1, result.getTotalElements());
        assertEquals(pub.getId(), result.getContent().get(0).getId());
    }

    @Test
    @DisplayName("keyword가 설명에만 있어도 검색됨")
    void search_keyword_matchesDescription() {
        Page<PublicDeckResponse> result = publicDeckService.search(tag + " 일본어", 0, 20, null);

        assertEquals(1, result.getTotalElements());
        assertEquals(pub2.getId(), result.getContent().get(0).getId());
    }

    @Test
    @DisplayName("빈 keyword는 전체 조회로 정규화")
    void search_blankKeyword() {
        List<Long> ids = publicDeckService.search("   ", 0, 100, null)
                .map(PublicDeckResponse::getId).getContent();
        // 전체 조회라 다른 데이터도 섞일 수 있음 — 우리 것 포함/제외만 단언
        assertTrue(ids.contains(pub.getId()));
        assertTrue(ids.contains(pub2.getId()));
        assertFalse(ids.contains(priv.getId()));
    }

    @Test
    @DisplayName("size 폭탄은 100으로 캡 (PageableUtils.safe)")
    void search_sizeCapped() {
        Page<PublicDeckResponse> result = publicDeckService.search(null, 0, 999_999, null);
        assertEquals(100, result.getSize());
    }

    @Test
    @DisplayName("인기 정렬 — like×5 + copy×3 점수 내림차순 (ADR-033)")
    void search_popular_ordersByScore() {
        User author = userRepository.save(User.builder()
                .email(tag + "pop@test.com").password("encoded").nickname("작가").build());
        // 점수: B(copy4=12) > A(like2=10) > C(like1+copy1=8)
        Deck a = deckRepository.save(Deck.builder().title(tag + "pop A").likeCount(2)
                .visibility(DeckVisibility.PUBLIC).user(author).build());
        Deck b = deckRepository.save(Deck.builder().title(tag + "pop B").copyCount(4)
                .visibility(DeckVisibility.PUBLIC).user(author).build());
        Deck c = deckRepository.save(Deck.builder().title(tag + "pop C").likeCount(1).copyCount(1)
                .visibility(DeckVisibility.PUBLIC).user(author).build());

        List<PublicDeckResponse> result = publicDeckService
                .search(tag + "pop", 0, 20, "popular").getContent();

        assertEquals(List.of(b.getId(), a.getId(), c.getId()),
                result.stream().map(PublicDeckResponse::getId).toList());
        assertEquals(4, result.get(0).getCopyCount());   // 순위 근거가 응답에 노출되는지
        assertEquals(2, result.get(1).getLikeCount());
    }

    @Test
    @DisplayName("인기 정렬 동점은 최신 생성 우선")
    void search_popular_tieBreaksByCreatedAt() {
        User author = userRepository.save(User.builder()
                .email(tag + "tie@test.com").password("encoded").nickname("작가").build());
        Deck older = deckRepository.save(Deck.builder().title(tag + "tie 먼저")
                .visibility(DeckVisibility.PUBLIC).user(author).build());
        Deck newer = deckRepository.save(Deck.builder().title(tag + "tie 나중")
                .visibility(DeckVisibility.PUBLIC).user(author).build());

        List<Long> ids = publicDeckService.search(tag + "tie", 0, 20, "popular")
                .map(PublicDeckResponse::getId).getContent();

        assertEquals(List.of(newer.getId(), older.getId()), ids);
    }

    @Test
    @DisplayName("sort에 이상한 값이면 400")
    void search_invalidSort_badRequest() {
        assertThrows(BadRequestException.class, () ->
                publicDeckService.search(tag, 0, 20, "hot"));
    }

    @Test
    @DisplayName("상세: PUBLIC/UNLISTED 조회 가능 — 작성자는 닉네임만 노출")
    void findOne_publicAndUnlisted() {
        assertEquals("decker", publicDeckService.findOne(pub.getId()).getAuthorNickname());
        assertEquals(unlisted.getId(), publicDeckService.findOne(unlisted.getId()).getId());
    }

    @Test
    @DisplayName("상세: PRIVATE과 없는 덱은 같은 404 — 메시지까지 동일 (존재 숨김)")
    void findOne_privateIndistinguishableFromMissing() {
        NotFoundException privateEx = assertThrows(NotFoundException.class,
                () -> publicDeckService.findOne(priv.getId()));
        NotFoundException missingEx = assertThrows(NotFoundException.class,
                () -> publicDeckService.findOne(999_999L));

        assertEquals(missingEx.getMessage(), privateEx.getMessage());
    }

    // === likedByMe / mine (React 백로그 ①③) ===

    private void loginAs(User u) {
        var principal = new com.vocamaster.auth.CustomUserDetails(u.getId(), u.getEmail());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("익명 조회 — likedByMe·mine 전부 false (예외 없이 통과)")
    void anonymous_flagsFalse() {
        PublicDeckResponse res = publicDeckService.findOne(pub.getId());
        assertFalse(res.isLikedByMe());
        assertFalse(res.isMine());
    }

    @Test
    @DisplayName("로그인 조회 — 내가 누른 덱만 likedByMe, 내 덱만 mine (목록 IN 한 방)")
    void loggedIn_flagsPerDeck() {
        User fan = userRepository.save(User.builder()
                .email(tag + "fan@test.com").password("encoded").nickname("팬").build());
        deckLikeService.like(pub.getId(), fan.getId());
        loginAs(fan);

        List<PublicDeckResponse> list = publicDeckService.search(tag, 0, 20, null).getContent();
        PublicDeckResponse likedOne = list.stream().filter(d -> d.getId().equals(pub.getId())).findFirst().orElseThrow();
        PublicDeckResponse other = list.stream().filter(d -> d.getId().equals(pub2.getId())).findFirst().orElseThrow();

        assertTrue(likedOne.isLikedByMe());
        assertFalse(other.isLikedByMe());
        assertFalse(likedOne.isMine(), "팬의 덱이 아님");

        // 덱 주인으로 보면 mine=true, 좋아요는 안 눌렀으니 false
        loginAs(deckOwner);
        PublicDeckResponse asOwner = publicDeckService.findOne(pub.getId());
        assertTrue(asOwner.isMine());
        assertFalse(asOwner.isLikedByMe());
    }

    // === 공개 카드 미리보기 (React 백로그 ②) ===

    @Test
    @DisplayName("공개 덱 카드는 익명도 조회 — position 순, PRIVATE은 상세와 같은 404")
    void publicCards_visibleAndPrivateHidden() {
        cardRepository.save(Card.builder().front("b").back("2").position(2).deck(pub).build());
        cardRepository.save(Card.builder().front("a").back("1").position(1).deck(pub).build());

        var page = publicDeckService.findCards(pub.getId(), 0, 50);
        assertEquals(2, page.getTotalElements());
        assertEquals("a", page.getContent().get(0).getFront(), "position 오름차순");

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> publicDeckService.findCards(priv.getId(), 0, 50));
        assertEquals(PublicDeckService.DECK_NOT_FOUND, ex.getMessage(), "존재 숨김 메시지 동일");
    }
}

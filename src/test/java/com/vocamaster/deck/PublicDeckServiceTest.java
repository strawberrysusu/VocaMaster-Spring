package com.vocamaster.deck;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.common.exception.NotFoundException;
import com.vocamaster.deck.dto.PublicDeckResponse;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PublicDeckServiceTest extends AbstractIntegrationTest {

    @Autowired private PublicDeckService publicDeckService;
    @Autowired private DeckRepository deckRepository;
    @Autowired private UserRepository userRepository;

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
        Page<PublicDeckResponse> result = publicDeckService.search(tag, 0, 20);

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
        Page<PublicDeckResponse> result = publicDeckService.search(tag + " 토익", 0, 20);

        assertEquals(1, result.getTotalElements());
        assertEquals(pub.getId(), result.getContent().get(0).getId());
    }

    @Test
    @DisplayName("keyword가 설명에만 있어도 검색됨")
    void search_keyword_matchesDescription() {
        Page<PublicDeckResponse> result = publicDeckService.search(tag + " 일본어", 0, 20);

        assertEquals(1, result.getTotalElements());
        assertEquals(pub2.getId(), result.getContent().get(0).getId());
    }

    @Test
    @DisplayName("빈 keyword는 전체 조회로 정규화")
    void search_blankKeyword() {
        List<Long> ids = publicDeckService.search("   ", 0, 100)
                .map(PublicDeckResponse::getId).getContent();
        // 전체 조회라 다른 데이터도 섞일 수 있음 — 우리 것 포함/제외만 단언
        assertTrue(ids.contains(pub.getId()));
        assertTrue(ids.contains(pub2.getId()));
        assertFalse(ids.contains(priv.getId()));
    }

    @Test
    @DisplayName("size 폭탄은 100으로 캡 (PageableUtils.safe)")
    void search_sizeCapped() {
        Page<PublicDeckResponse> result = publicDeckService.search(null, 0, 999_999);
        assertEquals(100, result.getSize());
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
}

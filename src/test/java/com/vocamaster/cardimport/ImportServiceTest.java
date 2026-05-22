package com.vocamaster.cardimport;

import com.vocamaster.card.CardRepository;
import com.vocamaster.cardimport.dto.ImportRequest;
import com.vocamaster.cardimport.dto.ImportResponse;
import com.vocamaster.cardimport.dto.PreviewResponse;
import com.vocamaster.common.exception.BadRequestException;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckRepository;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import com.vocamaster.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class ImportServiceTest extends AbstractIntegrationTest {

    @Autowired
    private ImportService importService;
    @Autowired
    private CardRepository cardRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DeckRepository deckRepository;

    private User user;
    private Deck deck;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .email("import@test.com")
                .password("encoded")
                .nickname("importer")
                .build());

        deck = deckRepository.save(Deck.builder()
                .title("Import Deck")
                .user(user)
                .build());
    }

    @Test
    @DisplayName("미리보기 - 정상 파싱 + 실패 줄 분리")
    void preview_parseAndFail() {
        ImportRequest req = new ImportRequest();
        req.setText("apple - red fruit\nbanana - yellow fruit\nbadline\n - empty front");

        PreviewResponse result = importService.preview(req);

        assertEquals(2, result.getTotalParsed());
        assertEquals(2, result.getFailedCount());
    }

    @Test
    @DisplayName("Import 실행 - DB에 카드 저장")
    void importCards_success() {
        ImportRequest req = new ImportRequest();
        req.setText("cat - small pet\ndog - loyal animal\nbird - flying creature");

        ImportResponse result = importService.importCards(deck.getId(), user.getId(), req);

        assertEquals(3, result.getImported());
        assertEquals(3, cardRepository.countByDeckId(deck.getId()));
    }

    @Test
    @DisplayName("커스텀 구분자 사용")
    void importCards_customSeparator() {
        ImportRequest req = new ImportRequest();
        req.setText("apple|red fruit\nbanana|yellow fruit");
        req.setSeparator("|");

        ImportResponse result = importService.importCards(deck.getId(), user.getId(), req);

        assertEquals(2, result.getImported());
    }

    @Test
    @DisplayName("1000줄 초과시 전체 거부")
    void importCards_overLimit_rejected() {
        ImportRequest req = new ImportRequest();
        req.setText("apple - 사과\n".repeat(1001));    //1001줄 = 상한 초과

        assertThrows(BadRequestException.class, () ->
                importService.importCards(deck.getId(), user.getId(), req));
    }

    @Test
    @DisplayName("구분자 자동 감지 - separator 미 지정시 콤마/탭 감지")
    void detectSeparator_auto() {

        // 콤마 — separator 안 줌 → 자동 감지
        ImportRequest comma = new ImportRequest();
        comma.setText("apple,사과\nbanana,바나나");
        assertEquals(2, importService.importCards(deck.getId(), user.getId(), comma).getImported());

        // 탭 — separator 안 줌 → 자동 감지
        ImportRequest tab = new ImportRequest();
        tab.setText("cat\t고양이\ndog\t개");
        assertEquals(2, importService.importCards(deck.getId(), user.getId(), tab).getImported());
    }
    @Test
    @DisplayName("중복카드 - 이미 있는 front 는 skip")
    void importCards_skipDuplicate(){
        // given: 1차 import — apple, banana 등록
        ImportRequest first = new ImportRequest();
        first.setText("apple,사과\nbanana,바나나");
        importService.importCards(deck.getId(), user.getId(), first);

        // when: 2차 import — apple·banana 중복 + cherry 신규
        ImportRequest second = new ImportRequest();
        second.setText("apple,사과\nbanana,바나나\ncherry,체리");
        ImportResponse result = importService.importCards(deck.getId(), user.getId(), second);

        // then: cherry만 등록, apple·banana는 skip
        assertEquals(1, result.getImported());
        assertEquals(2, result.getSkipped());
        assertEquals(3, cardRepository.countByDeckId(deck.getId()));   // 1차 2개 + cherry 1개
    }
}

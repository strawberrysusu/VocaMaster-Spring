package com.vocamaster.cardimport;

import com.vocamaster.card.CardRepository;
import com.vocamaster.cardimport.dto.ImportRequest;
import com.vocamaster.cardimport.dto.ImportResponse;
import com.vocamaster.cardimport.dto.PreviewResponse;
import com.vocamaster.deck.Deck;
import com.vocamaster.deck.DeckRepository;
import com.vocamaster.user.User;
import com.vocamaster.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ImportServiceTest {

    @Autowired private ImportService importService;
    @Autowired private CardRepository cardRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DeckRepository deckRepository;

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
}

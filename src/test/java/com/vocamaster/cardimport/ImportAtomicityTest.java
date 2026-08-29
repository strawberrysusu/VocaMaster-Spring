package com.vocamaster.cardimport;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.auth.AuthService;
import com.vocamaster.auth.dto.RegisterRequest;
import com.vocamaster.card.CardRepository;
import com.vocamaster.cardimport.dto.ImportFileRequest;
import com.vocamaster.cardimport.dto.ImportResponse;
import com.vocamaster.common.exception.BadRequestException;
import com.vocamaster.deck.DeckRepository;
import com.vocamaster.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 파일 임포트 원자성 (Codex 검산 2026-08-29).
 * 예전엔 프론트가 "덱 생성 → 카드 등록" 두 요청을 보내 두 번째가 실패하면 빈 덱이 남았다.
 * /decks/import-file 은 둘을 한 트랜잭션으로 — 실패 시 덱 생성까지 롤백.
 *
 * 자동 롤백을 끈(NOT_SUPPORTED) 이유: 테스트 트랜잭션에 서비스가 합류하면 서비스의
 * 커밋/롤백 경계가 사라져 "덱까지 롤백되는가"를 검증할 수 없다 (AuthServiceReuseCommitTest 패턴).
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ImportAtomicityTest extends AbstractIntegrationTest {

    @Autowired private ImportService importService;
    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private DeckRepository deckRepository;
    @Autowired private CardRepository cardRepository;

    private Long registerUser() {
        String email = "imp_" + System.nanoTime() + "@test.com";
        RegisterRequest req = new RegisterRequest();
        req.setEmail(email);
        req.setPassword("Passw0rd!");
        req.setNickname("임포트");
        authService.register(req, "test-ua", "127.0.0.1");
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    @Test
    @DisplayName("정상 경로 — 덱 생성과 카드 등록이 한 번에")
    void createDeckAndImport_success() {
        Long userId = registerUser();
        ImportFileRequest req = new ImportFileRequest();
        req.setTitle("원자 임포트 덱");
        req.setText("会議\tかいぎ\t회의\n学校\tがっこう\t학교");

        ImportResponse res = importService.createDeckAndImport(userId, req);

        assertNotNull(res.getDeckId());
        assertEquals(2, res.getImported());
        assertEquals(2, cardRepository.countByDeckId(res.getDeckId()));
    }

    @Test
    @DisplayName("카드 등록이 거부되면(줄 수 초과) 덱 생성까지 롤백 — 빈 덱이 남지 않는다")
    void createDeckAndImport_rollsBackDeckOnFailure() {
        Long userId = registerUser();
        long decksBefore = deckRepository.count();

        ImportFileRequest req = new ImportFileRequest();
        req.setTitle("롤백되어야 하는 덱");
        req.setText("단어\t뜻\n".repeat(1001));   // MAX_LINES(1000) 초과 → BadRequest

        assertThrows(BadRequestException.class, () -> importService.createDeckAndImport(userId, req));

        assertEquals(decksBefore, deckRepository.count(), "실패했으면 빈 덱도 남으면 안 됨");
    }
}

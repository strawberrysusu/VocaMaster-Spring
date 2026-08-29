package com.vocamaster.folder;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.auth.AuthService;
import com.vocamaster.auth.dto.RegisterRequest;
import com.vocamaster.deck.DeckRepository;
import com.vocamaster.deck.DeckService;
import com.vocamaster.deck.dto.CreateDeckRequest;
import com.vocamaster.deck.dto.DeckResponse;
import com.vocamaster.folder.dto.FolderRequest;
import com.vocamaster.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 폴더 삭제의 SET NULL·덱 수정의 카운터 보존 — 실제 커밋 경계에서 검증 (Codex 검산 8/29).
 * FolderTest는 테스트 트랜잭션(1차 캐시) 안이라 FK SET NULL의 DB 실값을 못 본다 —
 * 여기서는 자동 롤백을 꺼(NOT_SUPPORTED) 운영과 동일한 커밋 후 상태를 재조회한다.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FolderCommitBoundaryTest extends AbstractIntegrationTest {

    @Autowired private FolderService folderService;
    @Autowired private DeckService deckService;
    @Autowired private DeckRepository deckRepository;
    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager txManager;

    private Long registerUser() {
        String email = "fcb_" + System.nanoTime() + "@test.com";
        RegisterRequest req = new RegisterRequest();
        req.setEmail(email);
        req.setPassword("Passw0rd!");
        req.setNickname("경계테스트");
        authService.register(req, "test-ua", "127.0.0.1");
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    @Test
    @DisplayName("폴더 삭제 커밋 후 — 덱의 folder_id가 DB에서 실제로 NULL (FK SET NULL)")
    void folderDelete_setsNullInDb() {
        Long userId = registerUser();
        FolderRequest fr = new FolderRequest();
        fr.setName("커밋 경계 폴더");
        Long folderId = folderService.create(userId, fr).getId();

        CreateDeckRequest cr = new CreateDeckRequest();
        cr.setTitle("SET NULL 검증 덱");
        DeckResponse deck = deckService.create(userId, cr);
        deckService.moveToFolder(deck.getId(), userId, folderId);
        assertEquals(folderId, deckRepository.findById(deck.getId()).orElseThrow().getFolderId());

        folderService.remove(folderId, userId);

        // 커밋이 끝난 뒤의 새 조회 — 1차 캐시 없음, DB 실값
        assertNull(deckRepository.findById(deck.getId()).orElseThrow().getFolderId(),
                "폴더 삭제 후 덱은 미분류(NULL)여야");
    }

    @Test
    @DisplayName("제목 수정이 그 사이 오른 좋아요 카운터를 되돌리지 않는다 (@Transactional + @DynamicUpdate)")
    void update_preservesConcurrentCounter() {
        Long userId = registerUser();
        CreateDeckRequest cr = new CreateDeckRequest();
        cr.setTitle("카운터 보존 덱");
        DeckResponse deck = deckService.create(userId, cr);

        // 수정 트랜잭션 밖에서 카운터가 오른 상황 재현 — 원자적 UPDATE(별도 커밋).
        // @Modifying 쿼리는 트랜잭션이 필요한데 이 클래스는 NOT_SUPPORTED — 명시적 방을 만들어 실행
        new TransactionTemplate(txManager).executeWithoutResult(st -> deckRepository.incrementLikeCount(deck.getId()));

        var update = new com.vocamaster.deck.dto.UpdateDeckRequest();
        update.setTitle("제목만 바꿈");
        deckService.update(deck.getId(), userId, update);

        var after = deckRepository.findById(deck.getId()).orElseThrow();
        assertEquals("제목만 바꿈", after.getTitle());
        assertEquals(1, after.getLikeCount(), "제목 수정이 likeCount를 0으로 되돌리면 안 됨");
    }
}

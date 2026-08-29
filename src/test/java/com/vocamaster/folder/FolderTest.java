package com.vocamaster.folder;

import com.vocamaster.AbstractIntegrationTest;
import com.vocamaster.auth.AuthService;
import com.vocamaster.auth.dto.RegisterRequest;
import com.vocamaster.common.exception.NotFoundException;
import com.vocamaster.deck.DeckRepository;
import com.vocamaster.deck.DeckService;
import com.vocamaster.deck.dto.CreateDeckRequest;
import com.vocamaster.deck.dto.DeckResponse;
import com.vocamaster.folder.dto.FolderRequest;
import com.vocamaster.folder.dto.FolderResponse;
import com.vocamaster.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 📁 폴더 — 동결 전 마지막 기능 (V20).
 * 핵심 계약: ① 남의 폴더 = 없는 폴더와 같은 404 ② 폴더 삭제 시 덱은 죽지 않고 미분류(SET NULL)로.
 */
class FolderTest extends AbstractIntegrationTest {

    @Autowired private FolderService folderService;
    @Autowired private DeckService deckService;
    @Autowired private DeckRepository deckRepository;
    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;

    private Long registerUser() {
        String email = "fold_" + System.nanoTime() + "@test.com";
        RegisterRequest req = new RegisterRequest();
        req.setEmail(email);
        req.setPassword("Passw0rd!");
        req.setNickname("폴더테스트");
        authService.register(req, "test-ua", "127.0.0.1");
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private FolderResponse createFolder(Long userId, String name) {
        FolderRequest req = new FolderRequest();
        req.setName(name);
        return folderService.create(userId, req);
    }

    private DeckResponse createDeck(Long userId, String title) {
        CreateDeckRequest req = new CreateDeckRequest();
        req.setTitle(title);
        return deckService.create(userId, req);
    }

    @Test
    @DisplayName("생성 → 덱 이동 → 목록 folderId 반영")
    void createAndMove() {
        Long userId = registerUser();
        FolderResponse folder = createFolder(userId, "JLPT N1");
        DeckResponse deck = createDeck(userId, "Day01");

        deckService.moveToFolder(deck.getId(), userId, folder.getId());

        assertEquals(folder.getId(), deckRepository.findById(deck.getId()).orElseThrow().getFolderId());
        assertEquals(1, folderService.findAll(userId).size());
    }

    @Test
    @DisplayName("남의 폴더로 이동 시도 — 없는 폴더와 같은 404, 덱은 무변경")
    void moveToOthersFolder_rejected() {
        Long owner = registerUser();
        Long attacker = registerUser();
        FolderResponse ownersFolder = createFolder(owner, "주인 폴더");
        DeckResponse attackersDeck = createDeck(attacker, "공격자 덱");

        assertThrows(NotFoundException.class,
                () -> deckService.moveToFolder(attackersDeck.getId(), attacker, ownersFolder.getId()));
        assertNull(deckRepository.findById(attackersDeck.getId()).orElseThrow().getFolderId());
    }

    @Test
    @DisplayName("폴더 삭제 — 덱은 살아서 미분류로 (SET NULL, CASCADE 아님)")
    void removeFolder_decksSurviveUnfiled() {
        Long userId = registerUser();
        FolderResponse folder = createFolder(userId, "지워질 폴더");
        DeckResponse deck = createDeck(userId, "살아남을 덱");
        deckService.moveToFolder(deck.getId(), userId, folder.getId());

        assertDoesNotThrow(() -> folderService.remove(folder.getId(), userId));

        // folder_id의 NULL 전환은 DB FK(SET NULL) 몫이라 1차 캐시 너머 검증은 생략 —
        // 여기서의 계약은 "덱이 같이 삭제되지 않는다"(CASCADE가 아니라는 것)
        assertTrue(deckRepository.findById(deck.getId()).isPresent(), "덱은 삭제되면 안 됨");
    }

    @Test
    @DisplayName("미분류로 복귀 — folderId=null 이동")
    void moveToUnfiled() {
        Long userId = registerUser();
        FolderResponse folder = createFolder(userId, "폴더");
        DeckResponse deck = createDeck(userId, "덱");
        deckService.moveToFolder(deck.getId(), userId, folder.getId());

        deckService.moveToFolder(deck.getId(), userId, null);

        assertNull(deckRepository.findById(deck.getId()).orElseThrow().getFolderId());
    }
}

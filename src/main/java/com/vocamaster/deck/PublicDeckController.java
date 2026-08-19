package com.vocamaster.deck;

import com.vocamaster.card.dto.PublicCardResponse;
import com.vocamaster.common.CurrentUser;
import com.vocamaster.deck.dto.LikeResponse;
import com.vocamaster.deck.dto.PublicDeckResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Public Decks - 공개 단어장 (조회는 익명 가능, 좋아요는 로그인)")
@RestController
@RequestMapping("/public/decks")
@RequiredArgsConstructor
public class PublicDeckController {

    private final PublicDeckService publicDeckService;
    private final DeckLikeService deckLikeService;

    @GetMapping
    @Operation(summary = "공개 단어장 검색 — PUBLIC만, 제목/설명 LIKE. sort=recent(기본)|popular")
    public Page<PublicDeckResponse> search(@RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(defaultValue = "recent") String sort) {
        return publicDeckService.search(keyword, page, size, sort);
    }

    @GetMapping("/{deckId}")
    @Operation(summary = "공개 단어장 상세 — PUBLIC/UNLISTED 조회 가능, PRIVATE은 404")
    public PublicDeckResponse findOne(@PathVariable Long deckId) {
        return publicDeckService.findOne(deckId);
    }

    @GetMapping("/{deckId}/cards")
    @Operation(summary = "공개 단어장 카드 미리보기 — 복사 전 내용 확인용, 접근 규칙은 상세와 동일")
    public Page<PublicCardResponse> findCards(@PathVariable Long deckId,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "50") int size) {
        return publicDeckService.findCards(deckId, page, size);
    }

    @PostMapping("/{deckId}/like")
    @Operation(summary = "좋아요 (멱등 — 중복 클릭해도 1회)")
    public LikeResponse like(@PathVariable Long deckId) {
        try {
            return deckLikeService.like(deckId, CurrentUser.getId());
        } catch (DataIntegrityViolationException raceDuplicate) {
            // 더블탭 레이스: unique 위반으로 트랜잭션 전체(선행 카운트 증가 포함)가 롤백된 상태.
            // catch는 반드시 트랜잭션 프록시 '밖'인 여기서 — 안에서 잡으면 rollback-only 충돌 (ADR-032)
            return deckLikeService.currentState(deckId, CurrentUser.getId());
        }
    }

    @DeleteMapping("/{deckId}/like")
    @Operation(summary = "좋아요 취소 (멱등 — 안 눌렀어도 에러 없음)")
    public LikeResponse unlike(@PathVariable Long deckId) {
        return deckLikeService.unlike(deckId, CurrentUser.getId());
    }
}

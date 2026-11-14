package com.vocamaster.card;

import com.vocamaster.card.dto.CardResponse;
import com.vocamaster.card.dto.CreateCardRequest;
import com.vocamaster.card.dto.SetStarredRequest;
import com.vocamaster.card.dto.SetStarredResponse;
import com.vocamaster.card.dto.UpdateCardRequest;
import com.vocamaster.common.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Cards - 카드")
@RestController
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;

    @PostMapping("/decks/{deckId}/cards")
    @Operation(summary = "카드 생성")
    public CardResponse create(@PathVariable Long deckId, @Valid @RequestBody CreateCardRequest req) {
        return cardService.create(deckId, CurrentUser.getId(), req);
    }

    @GetMapping("/decks/{deckId}/cards")
    @Operation(summary = "카드 목록 (페이지네이션)")
    public Page<CardResponse> findAll(
            @PathVariable Long deckId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean starred) {
        return cardService.findAll(deckId, CurrentUser.getId(), page, size, keyword, starred,sort);
    }

    @GetMapping("/cards/{id}")
    @Operation(summary = "카드 상세 조회")
    public CardResponse findOne(@PathVariable Long id) {
        return cardService.findOne(id, CurrentUser.getId());
    }

    @PatchMapping("/cards/{id}")
    @Operation(summary = "카드 수정")
    public CardResponse update(@PathVariable Long id, @Valid @RequestBody UpdateCardRequest req) {
        return cardService.update(id, CurrentUser.getId(), req);
    }

    @DeleteMapping("/cards/{id}")
    @Operation(summary = "카드 삭제")
    public void remove(@PathVariable Long id) {
        cardService.remove(id, CurrentUser.getId());
    }

    @PatchMapping("/cards/{id}/star")
    @Operation(summary = "별표 토글")
    public CardResponse toggleStar(@PathVariable Long id) {
        return cardService.toggleStar(id, CurrentUser.getId());
    }

    @PatchMapping("/decks/{deckId}/cards/star")
    @Operation(summary = "별표 일괄 설정 (멱등)",
            description = "온라인 시험지에서 틀린 카드를 한 번에 ⭐ 하거나 되돌릴 때. changedIds = 실제로 값이 바뀐 카드, "
                    + "skippedIds = 이 덱에 없어 건너뛴 카드(시험 도중 지워졌거나 다른 덱)")
    public SetStarredResponse setStarred(@PathVariable Long deckId, @Valid @RequestBody SetStarredRequest req) {
        return cardService.setStarred(deckId, CurrentUser.getId(), req);
    }
}

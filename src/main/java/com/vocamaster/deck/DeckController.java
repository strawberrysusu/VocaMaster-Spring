package com.vocamaster.deck;

import com.vocamaster.common.CurrentUser;
import com.vocamaster.deck.dto.CreateDeckRequest;
import com.vocamaster.deck.dto.DeckResponse;
import com.vocamaster.deck.dto.UpdateDeckRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Decks - 단어장")
@RestController
@RequestMapping("/decks")
@RequiredArgsConstructor
public class DeckController {

    private final DeckService deckService;

    @PostMapping
    @Operation(summary = "단어장 생성")
    public DeckResponse create(@Valid @RequestBody CreateDeckRequest req) {
        return deckService.create(CurrentUser.getId(), req);
    }

    @GetMapping
    @Operation(summary = "내 단어장 목록")
    public List<DeckResponse> findAll() {
        return deckService.findAll(CurrentUser.getId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "단어장 상세 조회")
    public DeckResponse findOne(@PathVariable Long id) {
        return deckService.findOne(id, CurrentUser.getId());
    }

    @PatchMapping("/{id}")
    @Operation(summary = "단어장 수정")
    public DeckResponse update(@PathVariable Long id, @RequestBody UpdateDeckRequest req) {
        return deckService.update(id, CurrentUser.getId(), req);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "단어장 삭제 (카드도 함께 삭제)")
    public Map<String, String> remove(@PathVariable Long id) {
        deckService.remove(id, CurrentUser.getId());
        return Map.of("message", "삭제되었습니다");
    }
}

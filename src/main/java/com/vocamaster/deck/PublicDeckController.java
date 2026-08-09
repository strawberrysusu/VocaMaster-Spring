package com.vocamaster.deck;

import com.vocamaster.deck.dto.PublicDeckResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Public Decks - 공개 단어장 (인증 불필요)")
@RestController
@RequestMapping("/public/decks")
@RequiredArgsConstructor
public class PublicDeckController {

    private final PublicDeckService publicDeckService;

    @GetMapping
    @Operation(summary = "공개 단어장 검색 — PUBLIC만, 제목/설명 LIKE")
    public Page<PublicDeckResponse> search(@RequestParam(required = false) String keyword,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return publicDeckService.search(keyword, page, size);
    }

    @GetMapping("/{deckId}")
    @Operation(summary = "공개 단어장 상세 — PUBLIC/UNLISTED 조회 가능, PRIVATE은 404")
    public PublicDeckResponse findOne(@PathVariable Long deckId) {
        return publicDeckService.findOne(deckId);
    }
}

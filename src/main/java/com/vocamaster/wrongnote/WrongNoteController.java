package com.vocamaster.wrongnote;

import com.vocamaster.common.CurrentUser;
import com.vocamaster.wrongnote.dto.WrongNoteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Wrong Note - 통합 오답노트 (ADR-028)")
@RestController
@RequestMapping("/decks/{deckId}/wrong-notes")
@RequiredArgsConstructor
public class WrongNoteController {

    private final WrongNoteService wrongNoteService;

    @GetMapping
    @Operation(summary = "통합 오답노트 — Quiz/Typing/Flashcard 3 모드 합쳐 조회 (기본 최근 30일, days=0이면 전체)")
    public WrongNoteResponse get(
            @PathVariable Long deckId,
            @RequestParam(defaultValue = "30") int days) {
        return wrongNoteService.getWrongNotes(deckId, CurrentUser.getId(), days);
    }
}

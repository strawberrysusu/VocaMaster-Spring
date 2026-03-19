package com.vocamaster.study;

import com.vocamaster.common.CurrentUser;
import com.vocamaster.study.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Study - 플래시카드 학습")
@RestController
@RequiredArgsConstructor
public class StudyController {

    private final StudyService studyService;

    @PostMapping("/decks/{deckId}/study")
    @Operation(summary = "학습 세션 시작")
    public StudySessionResponse startSession(
            @PathVariable Long deckId,
            @Valid @RequestBody StartStudyRequest req) {
        return studyService.startSession(deckId, CurrentUser.getId(), req);
    }

    @PostMapping("/study/{sessionId}/record")
    @Operation(summary = "카드 학습 기록 (안다/모른다)")
    public StudyRecordResponse recordAnswer(
            @PathVariable Long sessionId,
            @Valid @RequestBody RecordStudyRequest req) {
        return studyService.recordAnswer(sessionId, CurrentUser.getId(), req);
    }

    @GetMapping("/study/{sessionId}/summary")
    @Operation(summary = "학습 세션 결과 요약 (모르는 카드 포함)")
    public StudySummaryResponse getSessionSummary(@PathVariable Long sessionId) {
        return studyService.getSessionSummary(sessionId, CurrentUser.getId());
    }

    @GetMapping("/decks/{deckId}/stats")
    @Operation(summary = "덱별 학습 통계 (플래시카드 + 퀴즈)")
    public DeckStatsResponse getDeckStats(@PathVariable Long deckId) {
        return studyService.getDeckStats(deckId, CurrentUser.getId());
    }
}

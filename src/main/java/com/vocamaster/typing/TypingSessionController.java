package com.vocamaster.typing;

import com.vocamaster.common.CurrentUser;
import com.vocamaster.typing.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Typing Session - 타이핑 모드 (ADR-026)")
@RestController
@RequestMapping("/decks/{deckId}/typing-sessions")
@RequiredArgsConstructor
public class TypingSessionController {

    private final TypingService typingService;

    @PostMapping
    @Operation(summary = "타이핑 세션 시작 — N문제 미리 생성 (Eager, 선택지 없음)")
    public StartTypingSessionResponse start(
            @PathVariable Long deckId,
            @Valid @RequestBody StartTypingSessionRequest req) {
        return typingService.startSession(deckId, CurrentUser.getId(), req);
    }

    @PostMapping("/{sessionId}/answers")
    @Operation(summary = "타이핑 답 제출 — trim + ignoreCase + 쉼표 복수 정답 분리 채점")
    public SubmitTypedAnswerResponse submit(
            @PathVariable Long deckId,
            @PathVariable Long sessionId,
            @Valid @RequestBody SubmitTypedAnswerRequest req) {
        return typingService.submitTypedAnswer(sessionId, CurrentUser.getId(), req);
    }

    @GetMapping("/{sessionId}/summary")
    @Operation(summary = "타이핑 세션 요약 — 정답률 + 문제별 결과 (안 푼 정답=NULL)")
    public TypingSessionSummaryResponse summary(
            @PathVariable Long deckId,
            @PathVariable Long sessionId) {
        return typingService.getSummary(sessionId, CurrentUser.getId());
    }
}

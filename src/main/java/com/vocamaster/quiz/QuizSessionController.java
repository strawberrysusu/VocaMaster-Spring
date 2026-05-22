package com.vocamaster.quiz;

import com.vocamaster.common.CurrentUser;
import com.vocamaster.quiz.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Quiz Session - 세션 단위 퀴즈 (ADR-024)")
@RestController
@RequestMapping("/decks/{deckId}/quiz-sessions")
@RequiredArgsConstructor
public class QuizSessionController {

    private final QuizService quizService;

    @PostMapping
    @Operation(summary = "퀴즈 세션 시작 — N문제 미리 생성 (Eager)")
    public StartSessionResponse start(
            @PathVariable Long deckId,
            @Valid @RequestBody StartSessionRequest req) {
        return quizService.startSession(deckId, CurrentUser.getId(), req);
    }

    @PostMapping("/{sessionId}/answers")
    @Operation(summary = "세션 내 한 문제 답 제출 (마지막이면 자동 종료)")
    public SubmitToSessionResponse submit(
            @PathVariable Long deckId,
            @PathVariable Long sessionId,
            @Valid @RequestBody SubmitToSessionRequest req) {
        return quizService.submitAnswerToSession(sessionId, CurrentUser.getId(), req);
    }

    @GetMapping("/{sessionId}/summary")
    @Operation(summary = "세션 요약 — 정답률 + 문제별 결과 (안 푼 문제 정답은 NULL)")
    public SessionSummaryResponse summary(
            @PathVariable Long deckId,
            @PathVariable Long sessionId) {
        return quizService.getSummary(sessionId, CurrentUser.getId());
    }
}

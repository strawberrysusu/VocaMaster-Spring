package com.vocamaster.quiz;

import com.vocamaster.common.CurrentUser;
import com.vocamaster.quiz.dto.GenerateQuizRequest;
import com.vocamaster.quiz.dto.QuizHistoryResponse;
import com.vocamaster.quiz.dto.QuizQuestionResponse;
import com.vocamaster.quiz.dto.QuizResultResponse;
import com.vocamaster.quiz.dto.SubmitQuizRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Quiz - 5지선다 퀴즈")
@RestController
@RequestMapping("/decks/{deckId}/quiz")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @PostMapping("/generate")
    @Operation(summary = "5지선다 퀴즈 문제 생성 (최소 5장 필요)")
    public QuizQuestionResponse generate(
            @PathVariable Long deckId,
            @Valid @RequestBody GenerateQuizRequest req) {
        return quizService.generateQuiz(deckId, CurrentUser.getId(), req);
    }

    @PostMapping("/submit")
    @Operation(summary = "퀴즈 답안 제출 (서버 정답 판정)")
    public QuizResultResponse submit(
            @PathVariable Long deckId,
            @Valid @RequestBody SubmitQuizRequest req) {
        return quizService.submitAnswer(deckId, CurrentUser.getId(), req);
    }

    @GetMapping("/history")
    @Operation(summary = "퀴즈 기록 조회")
    public QuizHistoryResponse history(@PathVariable Long deckId) {
        return quizService.getHistory(deckId, CurrentUser.getId());
    }

    @GetMapping("/wrong-cards")
    @Operation(summary = "오답 카드 목록 (오답 노트)")
    public Map<String, Object> wrongCards(@PathVariable Long deckId) {
        return quizService.getWrongCards(deckId, CurrentUser.getId());
    }
}

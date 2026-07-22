package com.vocamaster.review;

import com.vocamaster.common.CurrentUser;
import com.vocamaster.review.dto.DueCardResponse;
import com.vocamaster.review.dto.ReviewAnswerRequest;
import com.vocamaster.review.dto.ReviewAnswerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Review - Leitner Box 복습 (ADR-029)")
@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/due")
    @Operation(summary = "복습 대상 카드 목록 — nextReviewAt이 지난 카드, 오래 기다린 순 (deckId 없으면 전체 덱)")
    public List<DueCardResponse> getDueCards(@RequestParam(required = false) Long deckId) {
        return reviewService.getDueCards(CurrentUser.getId(), deckId);
    }

    @PostMapping("/cards/{cardId}/answer")
    @Operation(summary = "정답/오답 기록(자기평가) — Leitner 박스 증감 + 다음 복습 시각 계산")
    public ReviewAnswerResponse recordAnswer(@PathVariable Long cardId,
                                             @Valid @RequestBody ReviewAnswerRequest request) {
        return reviewService.recordAnswer(CurrentUser.getId(), cardId, request.getCorrect());
    }
}

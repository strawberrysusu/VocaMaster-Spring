package com.vocamaster.review;

import com.vocamaster.common.CurrentUser;
import com.vocamaster.review.dto.BoxCountResponse;
import com.vocamaster.review.dto.DueCardResponse;
import com.vocamaster.review.dto.ReviewAnswerRequest;
import com.vocamaster.review.dto.ReviewAnswerResponse;
import com.vocamaster.review.dto.TodaySummaryResponse;
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

    @GetMapping("/box-distribution")
    @Operation(summary = "라이트너 박스별 카드 분포 — 항상 6칸 (빈 박스는 0), 홈 사다리 차트용")
    public List<BoxCountResponse> getBoxDistribution() {
        return reviewService.getBoxDistribution(CurrentUser.getId());
    }

    @GetMapping("/today-summary")
    @Operation(summary = "오늘 학습 현황판 — 남은 복습 수 / 오늘 복습한 카드 수 / 오늘 전체 답변 수 / 연속 학습일 (오늘 학습 전엔 어제 streak 유지)")
    public TodaySummaryResponse getTodaySummary() {
        return reviewService.getTodaySummary(CurrentUser.getId());
    }

    @PostMapping("/cards/{cardId}/answer")
    @Operation(summary = "정답/오답 기록(자기평가) — Leitner 박스 증감 + 다음 복습 시각 계산")
    public ReviewAnswerResponse recordAnswer(@PathVariable Long cardId,
                                             @Valid @RequestBody ReviewAnswerRequest request) {
        return reviewService.recordAnswer(CurrentUser.getId(), cardId, request.getCorrect());
    }
}

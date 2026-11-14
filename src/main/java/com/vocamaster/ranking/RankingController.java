package com.vocamaster.ranking;

import com.vocamaster.auth.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Ranking - 학습 랭킹")
@RestController
@RequestMapping("/rankings")
@RequiredArgsConstructor
public class RankingController {

    private final StudyRankingService studyRankingService;

    @GetMapping("/study")
    @Operation(summary = "학습 랭킹 — 답한 카드 1장 = 1점. period=today(오늘, KST) | all(전체 합계), 최대 50명 + 내 순위")
    public StudyRankingResponse study(@AuthenticationPrincipal CustomUserDetails me,
                                      @RequestParam(defaultValue = "today") String period,
                                      @RequestParam(defaultValue = "50") int limit) {
        return studyRankingService.ranking(me.getUserId(), period, limit);
    }
}

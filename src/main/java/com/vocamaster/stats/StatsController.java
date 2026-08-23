package com.vocamaster.stats;

import com.vocamaster.common.CurrentUser;
import com.vocamaster.stats.dto.StatsOverviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Stats", description = "학습 통계")
@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping("/overview")
    @Operation(summary = "통계 화면 — 최근 28일 활동·연속·누적·라이트너 분포·덱별 진행률 (응답 하나)")
    public StatsOverviewResponse overview() {
        return statsService.getOverview(CurrentUser.getId());
    }
}

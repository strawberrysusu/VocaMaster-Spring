package com.vocamaster.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** 학습 랭킹에서 닉네임 가리기 켜기/끄기 (ADR-059) */
@Getter @Setter
public class RankingMaskRequest {

    @NotNull
    private Boolean masked;
}

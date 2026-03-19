package com.vocamaster.cardimport.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class ImportResponse {

    private int imported;
    private List<Map<String, Object>> failed;
    private int failedCount;
}

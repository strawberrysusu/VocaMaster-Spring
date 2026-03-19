package com.vocamaster.cardimport.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class PreviewResponse {

    private List<Map<String, String>> cards;
    private List<Map<String, Object>> failed;
    private int totalParsed;
    private int failedCount;
}

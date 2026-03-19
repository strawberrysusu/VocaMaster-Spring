package com.vocamaster.study.dto;

import com.vocamaster.study.StudyRecord;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class StudyRecordResponse {

    private Long id;
    private Long cardId;
    private Boolean known;
    private LocalDateTime createdAt;

    public static StudyRecordResponse from(StudyRecord record) {
        return StudyRecordResponse.builder()
                .id(record.getId())
                .cardId(record.getCard().getId())
                .known(record.getKnown())
                .createdAt(record.getCreatedAt())
                .build();
    }
}

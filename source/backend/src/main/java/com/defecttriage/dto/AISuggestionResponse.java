package com.defecttriage.dto;

import com.defecttriage.common.AISuggestionType;
import com.defecttriage.common.ReviewStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AISuggestionResponse {
    private Long id;
    private Long defectId;
    private AISuggestionType type;
    private String content;
    private ReviewStatus status;
    private String modifiedContent;
    private String reviewNote;
    private String triggeredByName;
    private String reviewedByName;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
}

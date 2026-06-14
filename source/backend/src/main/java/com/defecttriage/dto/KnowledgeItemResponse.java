package com.defecttriage.dto;

import com.defecttriage.common.KnowledgeStatus;
import com.defecttriage.common.KnowledgeType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class KnowledgeItemResponse {
    private Long id;
    private Long defectId;
    private String defectTitle;
    private KnowledgeType type;
    private String title;
    private String content;
    private KnowledgeStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
}

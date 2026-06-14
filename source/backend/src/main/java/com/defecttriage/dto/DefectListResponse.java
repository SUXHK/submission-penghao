package com.defecttriage.dto;

import com.defecttriage.common.DefectStatus;
import com.defecttriage.common.Priority;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DefectListResponse {
    private Long id;
    private String title;
    private DefectStatus status;
    private Priority priority;
    private Integer severity;
    private String reporterName;
    private String assigneeName;
    private LocalDateTime createdAt;
}

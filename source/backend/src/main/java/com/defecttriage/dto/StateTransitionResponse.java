package com.defecttriage.dto;

import com.defecttriage.common.DefectStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class StateTransitionResponse {
    private Long id;
    private DefectStatus fromStatus;
    private DefectStatus toStatus;
    private String operatorName;
    private String note;
    private LocalDateTime createdAt;
}

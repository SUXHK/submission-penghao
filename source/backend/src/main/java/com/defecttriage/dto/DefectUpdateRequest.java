package com.defecttriage.dto;

import com.defecttriage.common.DefectStatus;
import com.defecttriage.common.Priority;
import lombok.Data;

@Data
public class DefectUpdateRequest {
    private String title;
    private String description;
    private String phenomenon;
    private String environment;
    private String reproductionSteps;
    private String expectedResult;
    private String actualResult;
    private DefectStatus status;
    private Integer severity;
    private Priority priority;
    private Integer userImpact;
    private Integer businessImpact;
    private Integer frequency;
    private Integer workaround;
    private Integer releaseWindow;
    private String rootCauseHypothesis;
    private String fixPlan;
    private String fixContent;
    private String affectedModules;
    private Integer fixDuration;
    private String verificationResult;
    private String regressionScope;
    private String verificationConclusion;
}

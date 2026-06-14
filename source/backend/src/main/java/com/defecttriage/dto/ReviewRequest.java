package com.defecttriage.dto;

import com.defecttriage.common.ReviewStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReviewRequest {
    @NotNull
    private ReviewStatus status;
    private String modifiedContent;
    private String reviewNote;
}

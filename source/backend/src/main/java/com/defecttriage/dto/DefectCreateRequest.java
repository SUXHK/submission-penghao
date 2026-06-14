package com.defecttriage.dto;

import lombok.Data;

@Data
public class DefectCreateRequest {
    private String title;
    private String description;
    private String phenomenon;
    private String environment;
    private String reproductionSteps;
    private String expectedResult;
    private String actualResult;
}

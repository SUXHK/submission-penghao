package com.defecttriage.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AttachmentResponse {
    private Long id;
    private String originalFilename;
    private Long fileSize;
    private String mimeType;
    private LocalDateTime uploadedAt;
}

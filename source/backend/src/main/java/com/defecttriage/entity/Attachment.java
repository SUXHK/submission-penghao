package com.defecttriage.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "attachments")
@Getter @Setter @NoArgsConstructor
public class Attachment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "defect_id")
    private Defect defect;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private String originalFilename;

    @Column(nullable = false)
    private String filePath;

    private Long fileSize;

    private String mimeType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    private LocalDateTime uploadedAt;

    @PrePersist
    void prePersist() {
        uploadedAt = LocalDateTime.now();
    }
}

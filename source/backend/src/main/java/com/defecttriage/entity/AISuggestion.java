package com.defecttriage.entity;

import com.defecttriage.common.AISuggestionType;
import com.defecttriage.common.ReviewStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_suggestions")
@Getter @Setter @NoArgsConstructor
public class AISuggestion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "defect_id")
    private Defect defect;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AISuggestionType type;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus status = ReviewStatus.PENDING_REVIEW;

    @Column(columnDefinition = "TEXT")
    private String modifiedContent;

    @Column(columnDefinition = "TEXT")
    private String reviewNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "triggered_by")
    private User triggeredBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}

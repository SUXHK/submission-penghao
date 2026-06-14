package com.defecttriage.entity;

import com.defecttriage.common.DefectStatus;
import com.defecttriage.common.Priority;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "defects")
@Getter @Setter @NoArgsConstructor
public class Defect {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String phenomenon;

    @Column(columnDefinition = "TEXT")
    private String environment;

    @Column(columnDefinition = "TEXT")
    private String reproductionSteps;

    @Column(columnDefinition = "TEXT")
    private String expectedResult;

    @Column(columnDefinition = "TEXT")
    private String actualResult;

    private Integer severity;

    @Enumerated(EnumType.STRING)
    private Priority priority;

    private Integer userImpact;
    private Integer businessImpact;
    private Integer frequency;
    private Integer workaround;
    private Integer releaseWindow;

    @Column(columnDefinition = "TEXT")
    private String rootCauseHypothesis;

    @Column(columnDefinition = "TEXT")
    private String fixPlan;

    @Column(columnDefinition = "TEXT")
    private String fixContent;

    private String affectedModules;

    private Integer fixDuration;

    @Column(columnDefinition = "TEXT")
    private String verificationResult;

    @Column(columnDefinition = "TEXT")
    private String regressionScope;

    @Column(columnDefinition = "TEXT")
    private String verificationConclusion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DefectStatus status = DefectStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verifier_id")
    private User verifier;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime closedAt;

    @Version
    private Long version;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

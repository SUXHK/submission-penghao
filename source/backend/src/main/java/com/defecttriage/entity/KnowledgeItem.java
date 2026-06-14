package com.defecttriage.entity;

import com.defecttriage.common.KnowledgeStatus;
import com.defecttriage.common.KnowledgeType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_items")
@Getter @Setter @NoArgsConstructor
public class KnowledgeItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "defect_id")
    private Defect defect;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KnowledgeType type;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KnowledgeStatus status = KnowledgeStatus.AUTO_GENERATED;

    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}

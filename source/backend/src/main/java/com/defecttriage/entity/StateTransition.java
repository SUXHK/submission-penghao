package com.defecttriage.entity;

import com.defecttriage.common.DefectStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "state_transitions")
@Getter @Setter @NoArgsConstructor
public class StateTransition {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long defectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DefectStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DefectStatus toStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id")
    private User operator;

    @Column(columnDefinition = "TEXT")
    private String note;

    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}

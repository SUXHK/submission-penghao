package com.defecttriage.repository;

import com.defecttriage.entity.StateTransition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StateTransitionRepository extends JpaRepository<StateTransition, Long> {
    List<StateTransition> findByDefectIdOrderByCreatedAtDesc(Long defectId);
}

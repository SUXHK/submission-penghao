package com.defecttriage.repository;

import com.defecttriage.entity.AISuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AISuggestionRepository extends JpaRepository<AISuggestion, Long> {
    List<AISuggestion> findByDefectIdOrderByCreatedAtDesc(Long defectId);
    List<AISuggestion> findByDefectIdAndType(Long defectId, com.defecttriage.common.AISuggestionType type);
}

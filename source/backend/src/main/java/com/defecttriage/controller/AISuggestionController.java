package com.defecttriage.controller;

import com.defecttriage.common.AISuggestionType;
import com.defecttriage.dto.AISuggestionResponse;
import com.defecttriage.dto.ReviewRequest;
import com.defecttriage.entity.Defect;
import com.defecttriage.repository.DefectRepository;
import com.defecttriage.service.AISuggestionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AISuggestionController {

    private final AISuggestionService suggestionService;
    private final DefectRepository defectRepository;

    public AISuggestionController(AISuggestionService suggestionService,
                                   DefectRepository defectRepository) {
        this.suggestionService = suggestionService;
        this.defectRepository = defectRepository;
    }

    @GetMapping("/defects/{defectId}/ai-suggestions")
    public ResponseEntity<List<AISuggestionResponse>> list(@PathVariable Long defectId) {
        Defect defect = defectRepository.findById(defectId)
                .orElseThrow(() -> new RuntimeException("缺陷不存在"));
        return ResponseEntity.ok(suggestionService.getSuggestions(defectId, defect));
    }

    @PostMapping("/defects/{defectId}/ai-suggestions")
    public ResponseEntity<AISuggestionResponse> refresh(@PathVariable Long defectId,
                                                         @RequestParam AISuggestionType type) {
        Defect defect = defectRepository.findById(defectId)
                .orElseThrow(() -> new RuntimeException("缺陷不存在"));
        return ResponseEntity.ok(suggestionService.regenerateSuggestion(defect, type));
    }

    @PutMapping("/ai-suggestions/{id}/review")
    public ResponseEntity<AISuggestionResponse> review(@PathVariable Long id,
                                                        @Valid @RequestBody ReviewRequest req,
                                                        @RequestAttribute("userId") Long userId) {
        return ResponseEntity.ok(suggestionService.reviewSuggestion(id, req, userId));
    }
}

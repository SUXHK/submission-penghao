package com.defecttriage.service;

import com.defecttriage.common.AISuggestionType;
import com.defecttriage.common.BusinessException;
import com.defecttriage.common.DefectStatus;
import com.defecttriage.common.ForbiddenException;
import com.defecttriage.common.ReviewStatus;
import com.defecttriage.dto.AISuggestionResponse;
import com.defecttriage.dto.ReviewRequest;
import com.defecttriage.entity.AISuggestion;
import com.defecttriage.entity.Defect;
import com.defecttriage.entity.User;
import com.defecttriage.repository.AISuggestionRepository;
import com.defecttriage.repository.UserRepository;
import com.defecttriage.service.ai.DeepSeekClient;
import com.defecttriage.service.ai.PromptTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class AISuggestionService {

    private final AISuggestionRepository suggestionRepository;
    private final UserRepository userRepository;
    private final DeepSeekClient deepSeekClient;
    private final PromptTemplate promptTemplate;

    public AISuggestionService(AISuggestionRepository suggestionRepository,
                               UserRepository userRepository,
                               DeepSeekClient deepSeekClient,
                               PromptTemplate promptTemplate) {
        this.suggestionRepository = suggestionRepository;
        this.userRepository = userRepository;
        this.deepSeekClient = deepSeekClient;
        this.promptTemplate = promptTemplate;
    }

    public AISuggestion generateSuggestion(Defect defect, AISuggestionType type) {
        // Check if already exists — avoid duplicate generation
        List<AISuggestion> existing = suggestionRepository.findByDefectIdAndType(defect.getId(), type);
        if (!existing.isEmpty()) return existing.get(0);

        AISuggestion suggestion = new AISuggestion();
        suggestion.setDefect(defect);
        suggestion.setType(type);
        suggestion.setStatus(ReviewStatus.PENDING_REVIEW);
        suggestion.setTriggeredBy(defect.getAssignee());

        String content = switch (type) {
            case INVESTIGATION_PATH -> deepSeekClient.callDeepSeek(
                    promptTemplate.systemPrompt(), promptTemplate.investigationPathPrompt(defect));
            case ROOT_CAUSE -> deepSeekClient.callDeepSeek(
                    promptTemplate.systemPrompt(), promptTemplate.rootCausePrompt(defect));
            case FIX_PLAN -> deepSeekClient.callDeepSeek(
                    promptTemplate.systemPrompt(), promptTemplate.fixPlanPrompt(defect));
            case FIX_CONTENT -> deepSeekClient.callDeepSeek(
                    promptTemplate.systemPrompt(), promptTemplate.fixContentPrompt(defect));
            case TEST_SUGGESTION -> deepSeekClient.callDeepSeek(
                    promptTemplate.systemPrompt(), promptTemplate.testSuggestionPrompt(defect));
            case RETROSPECTIVE -> deepSeekClient.callDeepSeek(
                    promptTemplate.systemPrompt(), promptTemplate.retrospectivePrompt(defect));
        };
        suggestion.setContent(content);
        return suggestionRepository.save(suggestion);
    }

    public List<AISuggestionResponse> getSuggestions(Long defectId, Defect defect) {
        AISuggestionType expectedType = getExpectedTypeForStatus(defect.getStatus());
        if (expectedType != null) {
            List<AISuggestion> existing = suggestionRepository.findByDefectIdAndType(defectId, expectedType);
            if (existing.isEmpty()) {
                generateSuggestion(defect, expectedType);
            }
        }
        return suggestionRepository.findByDefectIdOrderByCreatedAtDesc(defectId).stream()
                .map(this::toResponse).toList();
    }

    private AISuggestionType getExpectedTypeForStatus(DefectStatus status) {
        return switch (status) {
            case TRIAGING -> AISuggestionType.INVESTIGATION_PATH;
            case ANALYZED -> AISuggestionType.ROOT_CAUSE;
            case PLANNED -> AISuggestionType.FIX_PLAN;
            case IN_REPAIR -> AISuggestionType.FIX_CONTENT;
            case FIXED -> AISuggestionType.TEST_SUGGESTION;
            case CLOSED -> AISuggestionType.RETROSPECTIVE;
            default -> null;
        };
    }

    public AISuggestionResponse regenerateSuggestion(Defect defect, AISuggestionType type) {
        List<AISuggestion> existing = suggestionRepository.findByDefectIdAndType(defect.getId(), type);
        suggestionRepository.deleteAll(existing);
        return toResponse(generateSuggestion(defect, type));
    }

    public AISuggestionResponse reviewSuggestion(Long id, ReviewRequest req, Long userId) {
        AISuggestion suggestion = suggestionRepository.findById(id)
                .orElseThrow(() -> new BusinessException("AI 建议不存在"));
        if (suggestion.getStatus() != ReviewStatus.PENDING_REVIEW) {
            throw new BusinessException("该建议已审核，不可重复审核");
        }
        if (req.getStatus() == ReviewStatus.REJECTED && (req.getReviewNote() == null || req.getReviewNote().isBlank())) {
            throw new BusinessException("拒绝时必须填写原因");
        }

        User reviewer = userRepository.findById(userId).orElseThrow();
        suggestion.setStatus(req.getStatus());
        suggestion.setReviewedBy(reviewer);
        suggestion.setReviewedAt(LocalDateTime.now());
        if (req.getModifiedContent() != null) {
            suggestion.setModifiedContent(req.getModifiedContent());
        }
        if (req.getReviewNote() != null) {
            suggestion.setReviewNote(req.getReviewNote());
        }
        return toResponse(suggestionRepository.save(suggestion));
    }

    public Optional<AISuggestion> findById(Long id) {
        return suggestionRepository.findById(id);
    }

    private AISuggestionResponse toResponse(AISuggestion s) {
        return AISuggestionResponse.builder()
                .id(s.getId())
                .defectId(s.getDefect() != null ? s.getDefect().getId() : null)
                .type(s.getType())
                .content(s.getContent())
                .status(s.getStatus())
                .modifiedContent(s.getModifiedContent())
                .reviewNote(s.getReviewNote())
                .triggeredByName(s.getTriggeredBy() != null ? s.getTriggeredBy().getDisplayName() : null)
                .reviewedByName(s.getReviewedBy() != null ? s.getReviewedBy().getDisplayName() : null)
                .createdAt(s.getCreatedAt())
                .reviewedAt(s.getReviewedAt())
                .build();
    }
}

package com.defecttriage.service.ai;

import com.defecttriage.common.AISuggestionType;
import com.defecttriage.common.DefectStatus;
import com.defecttriage.entity.Defect;
import com.defecttriage.service.AISuggestionService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AITriggerService {

    private final AISuggestionService suggestionService;

    public AITriggerService(AISuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @Async
    public void onStatusChanged(Defect defect, DefectStatus newStatus) {
        AISuggestionType type = switch (newStatus) {
            case TRIAGING -> AISuggestionType.INVESTIGATION_PATH;
            case ANALYZED -> AISuggestionType.ROOT_CAUSE;
            case PLANNED -> AISuggestionType.FIX_PLAN;
            case IN_REPAIR -> AISuggestionType.FIX_CONTENT;
            case FIXED -> AISuggestionType.TEST_SUGGESTION;
            case CLOSED -> AISuggestionType.RETROSPECTIVE;
            default -> null;
        };
        if (type != null) {
            suggestionService.generateSuggestion(defect, type);
        }
    }
}

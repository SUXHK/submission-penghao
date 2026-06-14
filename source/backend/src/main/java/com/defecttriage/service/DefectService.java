package com.defecttriage.service;

import com.defecttriage.common.*;
import com.defecttriage.dto.*;
import com.defecttriage.entity.Defect;
import com.defecttriage.entity.StateTransition;
import com.defecttriage.entity.User;
import com.defecttriage.repository.DefectRepository;
import com.defecttriage.repository.StateTransitionRepository;
import com.defecttriage.repository.UserRepository;
import com.defecttriage.service.ai.AITriggerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class DefectService {

    private final DefectRepository defectRepository;
    private final StateTransitionRepository transitionRepository;
    private final UserRepository userRepository;
    private final StateMachineService stateMachineService;
    private final DefectValidator defectValidator;
    private final AITriggerService aiTriggerService;
    private final KnowledgeService knowledgeService;

    public DefectService(DefectRepository defectRepository,
                         StateTransitionRepository transitionRepository,
                         UserRepository userRepository,
                         StateMachineService stateMachineService,
                         DefectValidator defectValidator,
                         AITriggerService aiTriggerService,
                         KnowledgeService knowledgeService) {
        this.defectRepository = defectRepository;
        this.transitionRepository = transitionRepository;
        this.userRepository = userRepository;
        this.stateMachineService = stateMachineService;
        this.defectValidator = defectValidator;
        this.aiTriggerService = aiTriggerService;
        this.knowledgeService = knowledgeService;
    }

    public List<DefectListResponse> listDefects(Optional<String> statusFilter, Optional<String> keyword) {
        List<Defect> defects;
        String kw = keyword.filter(k -> !k.isBlank()).orElse(null);

        if (statusFilter.isPresent() && !statusFilter.get().isBlank()) {
            DefectStatus status = DefectStatus.valueOf(statusFilter.get());
            if (kw != null) {
                defects = defectRepository.findByStatusInAndKeyword(List.of(status), kw);
            } else {
                defects = defectRepository.findByStatusIn(List.of(status));
            }
        } else if (kw != null) {
            defects = defectRepository.searchByKeyword(kw);
        } else {
            defects = defectRepository.findAll();
        }
        return defects.stream().map(this::toListResponse).toList();
    }

    public DefectResponse getDefect(Long id) {
        Defect defect = defectRepository.findById(id)
                .orElseThrow(() -> new BusinessException("缺陷不存在"));
        return toResponse(defect);
    }

    public DefectResponse createDefect(DefectCreateRequest req, Long userId) {
        User reporter = userRepository.findById(userId).orElseThrow();
        Defect defect = new Defect();
        defect.setTitle(req.getTitle());
        defect.setDescription(req.getDescription());
        defect.setPhenomenon(req.getPhenomenon());
        defect.setEnvironment(req.getEnvironment());
        defect.setReproductionSteps(req.getReproductionSteps());
        defect.setExpectedResult(req.getExpectedResult());
        defect.setActualResult(req.getActualResult());
        defect.setReporter(reporter);
        defect.setStatus(DefectStatus.DRAFT);
        return toResponse(defectRepository.save(defect));
    }

    public DefectResponse updateDefect(Long id, DefectUpdateRequest req, Long userId) {
        Defect defect = defectRepository.findById(id)
                .orElseThrow(() -> new BusinessException("缺陷不存在"));

        if (req.getStatus() != null && !req.getStatus().equals(defect.getStatus())) {
            throw new BusinessException("状态变更请使用流转接口 PATCH /api/defects/" + id + "/transition");
        }

        if (req.getTitle() != null) defect.setTitle(req.getTitle());
        if (req.getDescription() != null) defect.setDescription(req.getDescription());
        if (req.getPhenomenon() != null) defect.setPhenomenon(req.getPhenomenon());
        if (req.getEnvironment() != null) defect.setEnvironment(req.getEnvironment());
        if (req.getReproductionSteps() != null) defect.setReproductionSteps(req.getReproductionSteps());
        if (req.getExpectedResult() != null) defect.setExpectedResult(req.getExpectedResult());
        if (req.getActualResult() != null) defect.setActualResult(req.getActualResult());
        if (req.getSeverity() != null) defect.setSeverity(req.getSeverity());
        if (req.getPriority() != null) defect.setPriority(req.getPriority());
        if (req.getUserImpact() != null) defect.setUserImpact(req.getUserImpact());
        if (req.getBusinessImpact() != null) defect.setBusinessImpact(req.getBusinessImpact());
        if (req.getFrequency() != null) defect.setFrequency(req.getFrequency());
        if (req.getWorkaround() != null) defect.setWorkaround(req.getWorkaround());
        if (req.getReleaseWindow() != null) defect.setReleaseWindow(req.getReleaseWindow());
        if (req.getRootCauseHypothesis() != null) defect.setRootCauseHypothesis(req.getRootCauseHypothesis());
        if (req.getFixPlan() != null) defect.setFixPlan(req.getFixPlan());
        if (req.getFixContent() != null) defect.setFixContent(req.getFixContent());
        if (req.getAffectedModules() != null) defect.setAffectedModules(req.getAffectedModules());
        if (req.getFixDuration() != null) defect.setFixDuration(req.getFixDuration());
        if (req.getVerificationResult() != null) defect.setVerificationResult(req.getVerificationResult());
        if (req.getRegressionScope() != null) defect.setRegressionScope(req.getRegressionScope());
        if (req.getVerificationConclusion() != null) defect.setVerificationConclusion(req.getVerificationConclusion());

        return toResponse(defectRepository.save(defect));
    }

    public void deleteDefect(Long id, Long userId) {
        Defect defect = defectRepository.findById(id)
                .orElseThrow(() -> new BusinessException("缺陷不存在"));
        if (defect.getStatus() != DefectStatus.DRAFT) {
            throw new BusinessException("仅草稿状态可删除");
        }
        defectRepository.delete(defect);
    }

    @Transactional
    public DefectResponse transition(Long id, DefectStatus targetStatus, TransitionRequest req, Long userId) {
        User operator = userRepository.findById(userId).orElseThrow();
        Defect defect = defectRepository.findById(id)
                .orElseThrow(() -> new BusinessException("缺陷不存在"));

        DefectStatus fromStatus = defect.getStatus();
        UserRole userRole = operator.getRole();
        stateMachineService.validateTransition(fromStatus, targetStatus, userRole);

        switch (targetStatus) {
            case REPORTED -> defectValidator.validateRequiredFields(defect);
            case ANALYZED -> {
                defectValidator.validateAssessmentDimensions(defect);
                defect.setPriority(calculatePriority(defect));
            }
            case PLANNED -> defectValidator.validateRootCause(defect);
            case IN_REPAIR -> {
                defectValidator.validateFixPlan(defect);
                defect.setAssignee(operator);
            }
            case FIXED -> defectValidator.validateFixContent(defect);
            case VERIFIED -> {
                defectValidator.validateVerification(defect);
                defect.setVerifier(operator);
            }
            case CLOSED -> defect.setClosedAt(LocalDateTime.now());
        }

        defect.setStatus(targetStatus);
        defect = defectRepository.save(defect);

        StateTransition transition = new StateTransition();
        transition.setDefectId(defect.getId());
        transition.setFromStatus(fromStatus);
        transition.setToStatus(targetStatus);
        transition.setOperator(operator);
        transition.setNote(req.getNote());
        transitionRepository.save(transition);

        if (targetStatus == DefectStatus.CLOSED) {
            knowledgeService.generateForDefect(defect);
        }

        return toResponse(defect);
    }

    public List<StateTransitionResponse> getTransitions(Long defectId) {
        return transitionRepository.findByDefectIdOrderByCreatedAtDesc(defectId).stream()
                .map(t -> StateTransitionResponse.builder()
                        .id(t.getId())
                        .fromStatus(t.getFromStatus())
                        .toStatus(t.getToStatus())
                        .operatorName(t.getOperator() != null ? t.getOperator().getDisplayName() : null)
                        .note(t.getNote())
                        .createdAt(t.getCreatedAt())
                        .build())
                .toList();
    }

    public Priority calculatePriority(Defect defect) {
        double score = defect.getUserImpact() * 0.3
                + defect.getBusinessImpact() * 0.3
                + defect.getFrequency() * 0.2
                + defect.getWorkaround() * 0.1
                + defect.getReleaseWindow() * 0.1;
        if (score >= 4.5) return Priority.P0;
        if (score >= 3.5) return Priority.P1;
        if (score >= 2.5) return Priority.P2;
        if (score >= 1.5) return Priority.P3;
        return Priority.P4;
    }

    private DefectListResponse toListResponse(Defect d) {
        return DefectListResponse.builder()
                .id(d.getId()).title(d.getTitle()).status(d.getStatus())
                .priority(d.getPriority()).severity(d.getSeverity())
                .reporterName(d.getReporter() != null ? d.getReporter().getDisplayName() : null)
                .assigneeName(d.getAssignee() != null ? d.getAssignee().getDisplayName() : null)
                .createdAt(d.getCreatedAt())
                .build();
    }

    private DefectResponse toResponse(Defect d) {
        return DefectResponse.builder()
                .id(d.getId()).title(d.getTitle()).description(d.getDescription())
                .phenomenon(d.getPhenomenon()).environment(d.getEnvironment())
                .reproductionSteps(d.getReproductionSteps()).expectedResult(d.getExpectedResult())
                .actualResult(d.getActualResult()).severity(d.getSeverity())
                .priority(d.getPriority()).userImpact(d.getUserImpact())
                .businessImpact(d.getBusinessImpact()).frequency(d.getFrequency())
                .workaround(d.getWorkaround()).releaseWindow(d.getReleaseWindow())
                .rootCauseHypothesis(d.getRootCauseHypothesis()).fixPlan(d.getFixPlan())
                .fixContent(d.getFixContent()).affectedModules(d.getAffectedModules())
                .fixDuration(d.getFixDuration()).verificationResult(d.getVerificationResult())
                .regressionScope(d.getRegressionScope()).verificationConclusion(d.getVerificationConclusion())
                .status(d.getStatus())
                .reporterId(d.getReporter() != null ? d.getReporter().getId() : null)
                .reporterName(d.getReporter() != null ? d.getReporter().getDisplayName() : null)
                .assigneeId(d.getAssignee() != null ? d.getAssignee().getId() : null)
                .assigneeName(d.getAssignee() != null ? d.getAssignee().getDisplayName() : null)
                .verifierId(d.getVerifier() != null ? d.getVerifier().getId() : null)
                .verifierName(d.getVerifier() != null ? d.getVerifier().getDisplayName() : null)
                .createdAt(d.getCreatedAt()).updatedAt(d.getUpdatedAt()).closedAt(d.getClosedAt())
                .build();
    }
}

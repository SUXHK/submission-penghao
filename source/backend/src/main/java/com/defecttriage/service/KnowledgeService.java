package com.defecttriage.service;

import com.defecttriage.common.BusinessException;
import com.defecttriage.common.KnowledgeStatus;
import com.defecttriage.common.KnowledgeType;
import com.defecttriage.dto.KnowledgeItemResponse;
import com.defecttriage.dto.KnowledgeUpdateRequest;
import com.defecttriage.entity.Defect;
import com.defecttriage.entity.KnowledgeItem;
import com.defecttriage.repository.KnowledgeItemRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class KnowledgeService {

    private final KnowledgeItemRepository knowledgeItemRepository;

    public KnowledgeService(KnowledgeItemRepository knowledgeItemRepository) {
        this.knowledgeItemRepository = knowledgeItemRepository;
    }

    @Async
    public void generateForDefect(Defect defect) {
        generateItem(defect, KnowledgeType.REGRESSION_TEST,
                "回归用例: " + nvl(defect.getTitle()),
                "## 回归测试用例\n\n**来源缺陷**: " + nvl(defect.getTitle()) +
                "\n\n**测试场景**: 验证 " + nvl(defect.getPhenomenon()) +
                " 已修复\n\n**前置条件**: \n- 环境: " + nvl(defect.getEnvironment()) +
                "\n\n**测试步骤**: \n1. " + nvl(defect.getReproductionSteps()) +
                "\n\n**期望结果**: " + nvl(defect.getExpectedResult()) +
                "\n\n**验证结果**: " + nvl(defect.getVerificationResult()));

        generateItem(defect, KnowledgeType.TROUBLESHOOTING,
                "排查手册: " + nvl(defect.getTitle()),
                "## 排查手册\n\n**问题现象**: " + nvl(defect.getPhenomenon()) +
                "\n\n**可能原因**: \n" + nvl(defect.getRootCauseHypothesis()) +
                "\n\n**修复方案**: \n" + nvl(defect.getFixPlan()) +
                "\n\n**修复内容**: \n" + nvl(defect.getFixContent()));

        generateItem(defect, KnowledgeType.RISK_RULE,
                "风险规则: " + nvl(defect.getTitle()),
                "## 风险规则\n\n**风险描述**: " + nvl(defect.getTitle()) +
                "\n\n**影响模块**: " + nvl(defect.getAffectedModules()) +
                "\n\n**严重程度**: " + nvl(defect.getSeverity()) +
                "\n**优先级**: " + nvl(defect.getPriority()) +
                "\n\n**规避建议**: 请参考排查手册中对应的修复方案");
    }

    private void generateItem(Defect defect, KnowledgeType type, String title, String content) {
        KnowledgeItem item = new KnowledgeItem();
        item.setDefect(defect);
        item.setType(type);
        item.setTitle(title);
        item.setContent(content);
        item.setStatus(KnowledgeStatus.AUTO_GENERATED);
        knowledgeItemRepository.save(item);
    }

    public List<KnowledgeItemResponse> listAll(Optional<KnowledgeType> type, Optional<String> keyword) {
        KnowledgeType t = type.orElse(null);
        String kw = keyword.filter(k -> !k.isBlank()).orElse(null);
        return knowledgeItemRepository.findByTypeAndKeyword(t, kw).stream()
                .map(this::toResponse).toList();
    }

    public KnowledgeItemResponse getById(Long id) {
        KnowledgeItem item = knowledgeItemRepository.findById(id)
                .orElseThrow(() -> new BusinessException("知识条目不存在"));
        return toResponse(item);
    }

    public KnowledgeItemResponse update(Long id, KnowledgeUpdateRequest req) {
        KnowledgeItem item = knowledgeItemRepository.findById(id)
                .orElseThrow(() -> new BusinessException("知识条目不存在"));
        if (req.getTitle() != null) item.setTitle(req.getTitle());
        if (req.getContent() != null) item.setContent(req.getContent());
        return toResponse(knowledgeItemRepository.save(item));
    }

    public KnowledgeItemResponse publish(Long id) {
        KnowledgeItem item = knowledgeItemRepository.findById(id)
                .orElseThrow(() -> new BusinessException("知识条目不存在"));
        item.setStatus(KnowledgeStatus.PUBLISHED);
        item.setPublishedAt(LocalDateTime.now());
        return toResponse(knowledgeItemRepository.save(item));
    }

    private KnowledgeItemResponse toResponse(KnowledgeItem item) {
        return KnowledgeItemResponse.builder()
                .id(item.getId())
                .defectId(item.getDefect() != null ? item.getDefect().getId() : null)
                .defectTitle(item.getDefect() != null ? item.getDefect().getTitle() : null)
                .type(item.getType())
                .title(item.getTitle())
                .content(item.getContent())
                .status(item.getStatus())
                .createdAt(item.getCreatedAt())
                .publishedAt(item.getPublishedAt())
                .build();
    }

    private String nvl(Object o) {
        return o == null ? "" : o.toString();
    }
}

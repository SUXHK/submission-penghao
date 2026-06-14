package com.defecttriage.service;

import com.defecttriage.common.BusinessException;
import com.defecttriage.entity.Defect;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DefectValidator {

    public void validateRequiredFields(Defect defect) {
        List<String> missing = new ArrayList<>();
        if (isBlank(defect.getPhenomenon())) missing.add("现象描述");
        if (isBlank(defect.getEnvironment())) missing.add("运行环境");
        if (isBlank(defect.getReproductionSteps())) missing.add("复现步骤");
        if (isBlank(defect.getExpectedResult())) missing.add("期望结果");
        if (isBlank(defect.getActualResult())) missing.add("实际结果");
        if (isBlank(defect.getTitle())) missing.add("缺陷标题");
        if (!missing.isEmpty()) {
            throw new BusinessException("缺少必填信息: " + String.join(", ", missing));
        }
    }

    public void validateAssessmentDimensions(Defect defect) {
        List<String> missing = new ArrayList<>();
        if (defect.getUserImpact() == null) missing.add("用户影响");
        if (defect.getBusinessImpact() == null) missing.add("业务影响");
        if (defect.getFrequency() == null) missing.add("发生频率");
        if (defect.getWorkaround() == null) missing.add("规避方案");
        if (defect.getReleaseWindow() == null) missing.add("发布时间窗口");
        if (!missing.isEmpty()) {
            throw new BusinessException("缺少影响评估维度: " + String.join(", ", missing));
        }
    }

    public void validateRootCause(Defect defect) {
        if (isBlank(defect.getRootCauseHypothesis())) {
            throw new BusinessException("请先填写根因假设");
        }
    }

    public void validateFixPlan(Defect defect) {
        if (isBlank(defect.getFixPlan())) {
            throw new BusinessException("请先填写修复方案");
        }
    }

    public void validateFixContent(Defect defect) {
        if (isBlank(defect.getFixContent())) {
            throw new BusinessException("请先填写修复内容");
        }
    }

    public void validateVerification(Defect defect) {
        if (isBlank(defect.getVerificationResult()) || isBlank(defect.getRegressionScope())
                || isBlank(defect.getVerificationConclusion())) {
            throw new BusinessException("请完成验证记录（验证结果、回归范围、验证结论）");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

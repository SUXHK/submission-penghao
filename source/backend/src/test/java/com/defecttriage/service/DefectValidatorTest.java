package com.defecttriage.service;

import com.defecttriage.common.BusinessException;
import com.defecttriage.entity.Defect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefectValidatorTest {

    private final DefectValidator validator = new DefectValidator();
    private Defect defect;

    @BeforeEach
    void setUp() {
        defect = new Defect();
        defect.setTitle("Test Bug");
        defect.setPhenomenon("现象");
        defect.setEnvironment("环境");
        defect.setReproductionSteps("步骤");
        defect.setExpectedResult("期望");
        defect.setActualResult("实际");
    }

    @Test
    void shouldPassWhenAllRequiredFieldsFilled() {
        assertDoesNotThrow(() -> validator.validateRequiredFields(defect));
    }

    @Test
    void shouldFailWhenPhenomenonMissing() {
        defect.setPhenomenon(null);
        var ex = assertThrows(BusinessException.class, () -> validator.validateRequiredFields(defect));
        assertTrue(ex.getMessage().contains("现象描述"));
    }

    @Test
    void shouldFailWhenEnvironmentMissing() {
        defect.setEnvironment("");
        var ex = assertThrows(BusinessException.class, () -> validator.validateRequiredFields(defect));
        assertTrue(ex.getMessage().contains("运行环境"));
    }

    @Test
    void shouldPassWhenAllDimensionsFilled() {
        defect.setUserImpact(3);
        defect.setBusinessImpact(3);
        defect.setFrequency(3);
        defect.setWorkaround(3);
        defect.setReleaseWindow(3);
        assertDoesNotThrow(() -> validator.validateAssessmentDimensions(defect));
    }

    @Test
    void shouldFailWhenDimensionsMissing() {
        defect.setUserImpact(3);
        var ex = assertThrows(BusinessException.class, () -> validator.validateAssessmentDimensions(defect));
        assertTrue(ex.getMessage().contains("业务影响"));
    }

    @Test
    void shouldFailWhenRootCauseMissing() {
        assertThrows(BusinessException.class, () -> validator.validateRootCause(defect));
        defect.setRootCauseHypothesis("test");
        assertDoesNotThrow(() -> validator.validateRootCause(defect));
    }

    @Test
    void shouldFailWhenFixPlanMissing() {
        assertThrows(BusinessException.class, () -> validator.validateFixPlan(defect));
        defect.setFixPlan("plan");
        assertDoesNotThrow(() -> validator.validateFixPlan(defect));
    }

    @Test
    void shouldFailWhenFixContentMissing() {
        assertThrows(BusinessException.class, () -> validator.validateFixContent(defect));
        defect.setFixContent("content");
        assertDoesNotThrow(() -> validator.validateFixContent(defect));
    }

    @Test
    void shouldFailWhenVerificationMissing() {
        assertThrows(BusinessException.class, () -> validator.validateVerification(defect));
        defect.setVerificationResult("ok");
        assertThrows(BusinessException.class, () -> validator.validateVerification(defect));
        defect.setRegressionScope("all");
        assertThrows(BusinessException.class, () -> validator.validateVerification(defect));
        defect.setVerificationConclusion("pass");
        assertDoesNotThrow(() -> validator.validateVerification(defect));
    }
}

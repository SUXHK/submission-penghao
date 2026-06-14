package com.defecttriage.service.ai;

import com.defecttriage.entity.Defect;
import org.springframework.stereotype.Component;

@Component
public class PromptTemplate {

    public String systemPrompt() {
        return "你是一名资深 FSE（Field Service Engineer）工程师，专注于软件缺陷分析与修复。请根据提供的缺陷信息，给出专业的分析和建议。";
    }

    public String investigationPathPrompt(Defect defect) {
        return """
                请根据以下缺陷信息，生成结构化的排查路径建议。

                缺陷标题：%s
                现象描述：%s
                运行环境：%s
                复现步骤：%s
                期望结果：%s
                实际结果：%s

                请按以下格式输出：
                ## 可能原因分析
                （列出 2-3 个可能原因，按可能性从高到低排列）

                ## 排查步骤
                （按优先级列出具体排查步骤，每步说明目的和方法）

                ## 推荐工具
                （列出排查中建议使用的工具或命令）
                """.formatted(
                nvl(defect.getTitle()), nvl(defect.getPhenomenon()),
                nvl(defect.getEnvironment()), nvl(defect.getReproductionSteps()),
                nvl(defect.getExpectedResult()), nvl(defect.getActualResult()));
    }

    public String rootCausePrompt(Defect defect) {
        return """
                请根据以下缺陷信息和影响评估，分析可能的根因并给出假设。

                缺陷标题：%s
                现象描述：%s
                运行环境：%s
                影响范围评分 - 用户影响：%s, 业务影响：%s, 频率：%s, 规避方案：%s, 发布窗口：%s

                请按以下格式输出：
                ## 根因假设
                （基于现象和影响范围，给出最可能的根因假设，按置信度排序）

                ## 影响范围分析
                （分析该缺陷可能影响的功能模块和用户范围）

                ## 建议修复方向
                （给出修复建议的大方向，不需要具体代码）
                """.formatted(
                nvl(defect.getTitle()), nvl(defect.getPhenomenon()),
                nvl(defect.getEnvironment()),
                nvl(defect.getUserImpact()), nvl(defect.getBusinessImpact()),
                nvl(defect.getFrequency()), nvl(defect.getWorkaround()),
                nvl(defect.getReleaseWindow()));
    }

    public String fixPlanPrompt(Defect defect) {
        return """
                请根据以下缺陷的根因分析结果，生成具体的修复方案建议。

                缺陷标题：%s
                现象描述：%s
                根因假设：%s
                影响模块：%s

                请按以下格式输出：
                ## 修复目标
                （明确修复要达成的效果）

                ## 修复方案
                （给出具体可执行的修复方案，按步骤描述）

                ## 风险评估
                （评估修复过程中可能的风险和影响范围）
                """.formatted(
                nvl(defect.getTitle()), nvl(defect.getPhenomenon()),
                nvl(defect.getRootCauseHypothesis()),
                nvl(defect.getAffectedModules()));
    }

    public String fixContentPrompt(Defect defect) {
        return """
                请根据以下缺陷信息和修复方案，整理标准化的修复内容描述。

                缺陷标题：%s
                修复方案：%s
                影响模块：%s

                请按以下格式输出：
                ## 代码变更摘要
                （简要描述代码层面的改动）

                ## 具体修复内容
                （列出具体的修改点，每项一行）

                ## 验证要点
                （列出修复后需要重点验证的内容）
                """.formatted(
                nvl(defect.getTitle()), nvl(defect.getFixPlan()),
                nvl(defect.getAffectedModules()));
    }

    public String testSuggestionPrompt(Defect defect) {
        return """
                请根据以下缺陷修复信息，生成测试场景建议。

                缺陷标题：%s
                修复内容：%s
                影响模块：%s

                请按以下格式输出：
                ## 核心测试场景
                （列出 3-5 个必须验证的测试场景，包含前置条件和验证步骤）

                ## 回归范围建议
                （列出建议纳入回归测试的功能模块或场景）

                ## 边界条件
                （列出需要关注的边界条件和异常场景）
                """.formatted(
                nvl(defect.getTitle()), nvl(defect.getFixContent()),
                nvl(defect.getAffectedModules()));
    }

    public String retrospectivePrompt(Defect defect) {
        return """
                请根据以下已关闭的缺陷完整记录，生成复盘总结草稿。

                缺陷标题：%s
                缺陷描述：%s
                根因：%s
                修复方案：%s
                验证结论：%s
                严重程度：%s, 优先级：%s

                请按以下格式输出：
                ## 问题复盘
                （总结问题的起因、过程、影响）

                ## 经验教训
                （从本次缺陷中可以吸取哪些教训）

                ## 预防措施
                （建议采取哪些措施防止类似问题再次发生）
                """.formatted(
                nvl(defect.getTitle()), nvl(defect.getDescription()),
                nvl(defect.getRootCauseHypothesis()), nvl(defect.getFixPlan()),
                nvl(defect.getVerificationConclusion()),
                nvl(defect.getSeverity()), nvl(defect.getPriority()));
    }

    private String nvl(Object o) {
        return o == null ? "未填写" : o.toString();
    }
}

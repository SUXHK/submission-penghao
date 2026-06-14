# 缺陷分诊与修复闭环系统

FSE 候选人挑战 03：帮助工程师管理缺陷从受理到验证关闭的全过程，AI 辅助分析但不替代人工判断。

> **在线演示：[https://ai.suxh.top](https://ai.suxh.top)**
> 
> 演示账号：`submitter` / `engineer` / `qa`，密码均为 `admin123`

## 1. 项目背景

本系统是 FSE 端到端交付能力的展示项目。围绕缺陷处理的完整生命周期，设计并实现了：

- **9 状态流转机**：DRAFT → REPORTED → TRIAGING → ANALYZED → PLANNED → IN_REPAIR → FIXED → VERIFIED → CLOSED（含 REOPENED 共 10 种状态）
- **AI 辅助分析**：调用 DeepSeek API 自动生成排查路径、根因假设、修复计划建议、修复内容建议、测试建议和复盘草稿（6 种 AI 建议类型）
- **3 角色权限隔离**：提交人登记缺陷、工程师分析修复、QA 验证关闭
- **知识沉淀**：缺陷关闭后自动生成回归用例、排查手册和风险规则

## 2. 技术栈

| 类型   | 技术                                                                                                                         |
| ---- | -------------------------------------------------------------------------------------------------------------------------- |
| 前端   | React 19 + TypeScript 6 + Vite 8 + TanStack Router + TanStack Query + TanStack Table + Tailwind CSS 4 + shadcn/ui + Lucide |
| 后端   | Java 21 + Spring Boot 3.4 + Maven + Spring Data JPA                                                                        |
| 数据存储 | MySQL 8（Docker）                                                                                                            |
| 认证   | JWT（jjwt）                                                                                                                  |
| AI   | DeepSeek API（deepseek-v4-flash）                                                                                            |
| 测试   | JUnit 5 + MockMvc（后端），Playwright（E2E）                                                                                      |
| 部署   | Docker Compose                                                                                                             |

## 3. 核心功能

- 缺陷登记与信息完整性校验（6 项必填字段）
- 严重程度/优先级评估（5 维度加权计算）
- 9 状态流转机（含 REOPENED 共 10 种状态），每步有进入/退出条件
- AI 建议自动生成 + 人工三态审核（采纳/拒绝/修改）
- 流转审计日志
- 附件上传管理
- 知识库（自动生成回归用例/排查手册/风险规则）
- 统计栏（总计/紧急/已关闭 + 完成率进度环 + 状态分布色条）

## 4. 核心流程

```mermaid
flowchart LR
  A[创建缺陷] --> B[填写复现信息]
  B --> C[提交分诊]
  C --> D[分诊评估 5维度]
  D --> E[优先级自动计算]
  E --> F[根因假设 + AI建议]
  F --> G[修复计划]
  G --> H[执行修复]
  H --> I[QA验证]
  I --> J[缺陷关闭]
  J --> K[知识沉淀]
```

## 5. 本地启动

```bash
# 1. 启动 MySQL
docker run -d --name defect-triage-mysql \
  -e MYSQL_ROOT_PASSWORD=root123 \
  -e MYSQL_DATABASE=defect_triage \
  -p 3307:3306 mysql:8.0

# 2. 启动后端（端口 8081）
cd backend && mvn spring-boot:run

# 3. 启动前端（端口 3001）
cd source/frontend && npm install && npm run dev

# 4. 访问
open http://localhost:3001
```

## 6. 测试执行

```bash
# 后端单元测试
cd backend && mvn test

# E2E 浏览器测试
cd source/frontend && node e2e-test.mjs
```

## 7. 演示路径

1. 用 `submitter/admin123` 登录 → 点击「+ 创建缺陷」→ 填写表单 → 提交分诊
2. 用 `engineer/admin123` 登录 → 点击缺陷 → 评估维度 → 一步步流转到已修复
3. 查看右侧 AI 建议 → 采纳/修改/拒绝
4. 用 `qa/admin123` 登录 → 填写验证记录 → 关闭缺陷
5. 查看知识库 → 确认自动生成了知识条目

## 8. 演示账号

| 用户名       | 密码       | 角色  |
| --------- | -------- | --- |
| submitter | admin123 | 提交人 |
| engineer  | admin123 | 工程师 |
| qa        | admin123 | QA  |

## 9. AI 使用概述

- **模型**：Claude Sonnet 4.6（开发辅助）+ DeepSeek v4 flash（运行时 AI 建议）
- **开发阶段**：需求分析、技术设计、编码实现、测试验证全部由 AI 辅助
- **运行时**：前端查询 AI 建议时，后端按需同步调用 DeepSeek（60s 超时，3 次重试），生成结果即时返回
- **人工决策**：所有 AI 建议需人工审核（采纳/拒绝/修改）后方可生效，审核后自动回填对应字段

## 10. 已知问题

- 前端单元测试（Vitest）覆盖不足
- DeepSeek API 不可用时 AI 建议功能不可用
- 移动端未做响应式适配
- 乐观锁并发冲突提示不够友好
- 创建缺陷时如选择"提交分诊"失败，DRAFT 草稿会被自动清除（不回滚则留下脏数据）

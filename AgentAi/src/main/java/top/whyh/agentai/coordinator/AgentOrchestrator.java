package top.whyh.agentai.coordinator;

import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import top.whyh.agentai.model.dto.CodeReviewReport;
import top.whyh.agentai.model.dto.ReviewIssue;
import top.whyh.agentai.result.ArchitectResult;
import top.whyh.agentai.result.RequirementResult;
import top.whyh.agentai.result.SystemGenerateResult;
import top.whyh.agentai.service.ArchitectAnalysisService;
import top.whyh.agentai.service.CodeOutputService;
import top.whyh.agentai.service.CodeReviewService;
import top.whyh.agentai.service.RequirementAnalysisService;
import top.whyh.agentai.service.WorkflowService;
import top.whyh.agentai.service.codegen.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能体协调器：串联各智能体，支持动态启用/禁用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentOrchestrator {

    private static final List<Pattern> SYSTEM_NAME_PATTERNS = List.of(
            Pattern.compile("(?:构建|开发|编写|创建|生成|做一个|搞一个)?(?:一个)?(.*?系统)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:构建|开发|编写|创建|生成|做一个|搞一个)?(?:一个)?(.*?管理系统)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:构建|开发|编写|创建|生成|做一个|搞一个)?(?:一个)?(.*?平台)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:构建|开发|编写|创建|生成|做一个|搞一个)?(?:一个)?(.*?APP)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:构建|开发|编写|创建|生成|做一个|搞一个)?(?:一个)?(.*?小程序)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:构建|开发|编写|创建|生成|做一个|搞一个)?(?:一个)?(.*?网站)", Pattern.CASE_INSENSITIVE)
    );
    private static final int MAX_INPUT_LENGTH = 500;
    private static final Pattern ILLEGAL_CHAR_PATTERN = Pattern.compile("[\\\\/:*?\"<>|]");
    private static final int MAX_FIX_RETRIES = 3;

    private final RequirementAnalysisService requirementService;
    private final ArchitectAnalysisService architectService;
    private final CodeOutputService codeOutputService;
    private final BackendSkeletonGenerator backendSkeletonGenerator;
    private final BackendControllerGenerator backendControllerGenerator;
    private final BackendDomainGenerator backendDomainGenerator;
    private final FrontendSkeletonGenerator frontendSkeletonGenerator;
    private final FrontendViewsGenerator frontendViewsGenerator;
    private final FrontendApiGenerator frontendApiGenerator;
    private final CodeReviewService codeReviewService;
    private final LlmClient llmClient;
    private final FileBlockParser fileBlockParser;
    private final AgentRegistry agentRegistry;
    private final WorkflowService workflowService;
    private final ObjectMapper objectMapper;

    /** 用户主动取消任务时抛出的专用异常 */
    static class TaskCancelledException extends RuntimeException {
        TaskCancelledException(String msg) { super(msg); }
    }

    public SystemGenerateResult executeFullFlow(String userInput) {
        return executeFullFlow(userInput, null, null, null);
    }

    public SystemGenerateResult executeFullFlow(String userInput, Consumer<String> progressListener) {
        return executeFullFlow(userInput, progressListener, null, null);
    }

    public SystemGenerateResult executeFullFlow(String userInput, Consumer<String> progressListener, BooleanSupplier cancelCheck) {
        return executeFullFlow(userInput, progressListener, cancelCheck, null);
    }

    /**
     * 执行全流程，支持进度回调、取消检查和工作流 ID 记录。
     * @param workflowId 已在数据库创建的 workflow_instance.id；为 null 时跳过数据库记录
     */
    public SystemGenerateResult executeFullFlow(String userInput, Consumer<String> progressListener,
                                                BooleanSupplier cancelCheck, String workflowId) {
        long startTime = System.currentTimeMillis();
        String requestId = generateShortRequestId();
        log.info("开始执行智能体全流程 | requestId: {} | 用户输入: {}", requestId, maskSensitiveInput(userInput));

        try {
            validateUserInput(userInput);
            String systemName = extractSystemName(userInput);
            log.info("解析到有效系统名称 | requestId: {} | 系统名称: {}", requestId, systemName);
            if (progressListener != null) progressListener.accept("解析到有效系统名称：" + systemName);

            // ★ 从数据库实时读取已启用的智能体集合
            Set<String> enabledCodes = agentRegistry.getEnabledAgentCodes();
            agentRegistry.validateCoreAgentsEnabled(enabledCodes);
            log.info("当前已启用智能体: {}", enabledCodes);

            // ── 需求分析 ──
            if (progressListener != null) progressListener.accept("正在生成 PRD 文档...");
            workflowService.updateCurrentStep(workflowId, "需求分析");
            String reqAgentId = agentRegistry.getAgentIdByCode(AgentDefinition.REQUIREMENT_ANALYSIS.getAgentCode());
            long prdStart = System.currentTimeMillis();
            RequirementResult prdResult = requirementService.generateRequirementDocument(userInput);
            long prdCost = System.currentTimeMillis() - prdStart;
            log.info("PRD文档生成完成 | requestId: {} | 耗时: {}ms", requestId, prdCost);
            workflowService.recordStep(workflowId, reqAgentId, "需求分析", "生成 PRD 需求文档",
                    prdResult.getDocumentContent(), prdCost, "completed");
            checkCancellation(cancelCheck, progressListener, requestId);

            // ── 架构设计 ──
            if (progressListener != null) progressListener.accept("正在生成架构文档...");
            workflowService.updateCurrentStep(workflowId, "架构设计");
            String archAgentId = agentRegistry.getAgentIdByCode(AgentDefinition.ARCHITECT_DESIGN.getAgentCode());
            long archStart = System.currentTimeMillis();
            ArchitectResult archResult = architectService.generateArchitectDocument(
                    prdResult.getDocumentContent(), systemName);
            long archCost = System.currentTimeMillis() - archStart;
            log.info("架构文档生成完成 | requestId: {} | 耗时: {}ms", requestId, archCost);
            workflowService.recordStep(workflowId, archAgentId, "架构设计", "生成系统架构文档",
                    archResult.getDocumentContent(), archCost, "completed");
            checkCancellation(cancelCheck, progressListener, requestId);

            // ── 动态编排：代码生成 ──
            Map<String, String> projectFiles = new java.util.LinkedHashMap<>();

            if (agentRegistry.canExecute(AgentDefinition.BACKEND_SKELETON, enabledCodes)) {
                if (progressListener != null) progressListener.accept("正在生成后端项目骨架...");
                workflowService.updateCurrentStep(workflowId, "后端骨架生成");
                String agentId = agentRegistry.getAgentIdByCode(AgentDefinition.BACKEND_SKELETON.getAgentCode());
                long t0 = System.currentTimeMillis();
                Map<String, String> added = backendSkeletonGenerator.generateBackendSkeleton(
                        systemName, prdResult.getDocumentContent(), archResult.getDocumentContent());
                projectFiles.putAll(added);
                workflowService.recordStep(workflowId, agentId, "后端骨架生成", "生成 Spring Boot 项目骨架",
                        "生成文件数：" + added.size() + "\n" + String.join("\n", added.keySet()),
                        System.currentTimeMillis() - t0, "completed");
                checkCancellation(cancelCheck, progressListener, requestId);
            }

            if (agentRegistry.canExecute(AgentDefinition.BACKEND_CONTROLLER, enabledCodes)) {
                if (progressListener != null) progressListener.accept("正在生成后端接口 (Controller)...");
                workflowService.updateCurrentStep(workflowId, "Controller 生成");
                String agentId = agentRegistry.getAgentIdByCode(AgentDefinition.BACKEND_CONTROLLER.getAgentCode());
                long t0 = System.currentTimeMillis();
                Map<String, String> added = backendControllerGenerator.generateBackendControllers(
                        systemName, prdResult.getDocumentContent(), archResult.getDocumentContent());
                projectFiles.putAll(added);
                workflowService.recordStep(workflowId, agentId, "Controller 生成", "生成 RESTful Controller 层",
                        "生成文件数：" + added.size() + "\n" + String.join("\n", added.keySet()),
                        System.currentTimeMillis() - t0, "completed");
                checkCancellation(cancelCheck, progressListener, requestId);
            }

            if (agentRegistry.canExecute(AgentDefinition.BACKEND_DOMAIN, enabledCodes)) {
                if (progressListener != null) progressListener.accept("正在生成后端领域层 (Service/Entity/DTO)...");
                workflowService.updateCurrentStep(workflowId, "领域层生成");
                String agentId = agentRegistry.getAgentIdByCode(AgentDefinition.BACKEND_DOMAIN.getAgentCode());
                long t0 = System.currentTimeMillis();
                Map<String, String> added = backendDomainGenerator.generateBackendDomainLayer(
                        systemName, prdResult.getDocumentContent(), archResult.getDocumentContent());
                projectFiles.putAll(added);
                workflowService.recordStep(workflowId, agentId, "领域层生成", "生成 Service / Entity / DTO 层",
                        "生成文件数：" + added.size() + "\n" + String.join("\n", added.keySet()),
                        System.currentTimeMillis() - t0, "completed");
                checkCancellation(cancelCheck, progressListener, requestId);
            }

            if (agentRegistry.canExecute(AgentDefinition.FRONTEND_SKELETON, enabledCodes)) {
                if (progressListener != null) progressListener.accept("正在生成前端项目骨架...");
                workflowService.updateCurrentStep(workflowId, "前端骨架生成");
                String agentId = agentRegistry.getAgentIdByCode(AgentDefinition.FRONTEND_SKELETON.getAgentCode());
                long t0 = System.currentTimeMillis();
                Map<String, String> added = frontendSkeletonGenerator.generateFrontendSkeleton(
                        systemName, prdResult.getDocumentContent(), archResult.getDocumentContent());
                projectFiles.putAll(added);
                workflowService.recordStep(workflowId, agentId, "前端骨架生成", "生成 Vue3 + Vite 项目骨架",
                        "生成文件数：" + added.size() + "\n" + String.join("\n", added.keySet()),
                        System.currentTimeMillis() - t0, "completed");
                checkCancellation(cancelCheck, progressListener, requestId);
            }

            if (agentRegistry.canExecute(AgentDefinition.FRONTEND_VIEWS, enabledCodes)) {
                if (progressListener != null) progressListener.accept("正在生成前端页面 (Views)...");
                workflowService.updateCurrentStep(workflowId, "前端页面生成");
                String agentId = agentRegistry.getAgentIdByCode(AgentDefinition.FRONTEND_VIEWS.getAgentCode());
                long t0 = System.currentTimeMillis();
                Map<String, String> added = frontendViewsGenerator.generateFrontendViews(
                        systemName, prdResult.getDocumentContent(), archResult.getDocumentContent(), projectFiles);
                projectFiles.putAll(added);
                workflowService.recordStep(workflowId, agentId, "前端页面生成", "生成 Vue3 Views 页面",
                        "生成文件数：" + added.size() + "\n" + String.join("\n", added.keySet()),
                        System.currentTimeMillis() - t0, "completed");
                checkCancellation(cancelCheck, progressListener, requestId);
            }

            if (agentRegistry.canExecute(AgentDefinition.FRONTEND_API, enabledCodes)) {
                if (progressListener != null) progressListener.accept("正在封装前端 API 接口...");
                workflowService.updateCurrentStep(workflowId, "前端 API 封装");
                String agentId = agentRegistry.getAgentIdByCode(AgentDefinition.FRONTEND_API.getAgentCode());
                long t0 = System.currentTimeMillis();
                Map<String, String> added = frontendApiGenerator.generateFrontendApiUtils(
                        systemName, prdResult.getDocumentContent(), archResult.getDocumentContent(), projectFiles);
                projectFiles.putAll(added);
                workflowService.recordStep(workflowId, agentId, "前端 API 封装", "封装 Axios 接口层",
                        "生成文件数：" + added.size() + "\n" + String.join("\n", added.keySet()),
                        System.currentTimeMillis() - t0, "completed");
                checkCancellation(cancelCheck, progressListener, requestId);
            }

            log.info("项目代码合并完成 | requestId: {} | 文件总数: {}", requestId, projectFiles.size());

            // ── 沙箱部署保障（第一道防线：审查前强制写入正确配置） ──
            enforceDeploymentRequirements(projectFiles, systemName);

            // ── 代码审查与修复循环 ──
            String reviewWarning = "";
            if (agentRegistry.canExecute(AgentDefinition.CODE_REVIEW, enabledCodes)) {
                int attempt = 0;
                CodeReviewReport lastReport = null;
                String reviewAgentId = agentRegistry.getAgentIdByCode(AgentDefinition.CODE_REVIEW.getAgentCode());
                while (attempt < MAX_FIX_RETRIES) {
                    if (progressListener != null) progressListener.accept(
                            String.format("正在进行代码审查 (第%d/%d轮)...", attempt + 1, MAX_FIX_RETRIES));
                    workflowService.updateCurrentStep(workflowId, String.format("代码审查（第 %d 轮）", attempt + 1));
                    long t0 = System.currentTimeMillis();
                    CodeReviewReport report = codeReviewService.reviewCode(
                            systemName, prdResult.getDocumentContent(), archResult.getDocumentContent(), projectFiles);
                    lastReport = report;

                    List<ReviewIssue> criticalIssues = report.getCriticalIssues();
                    List<ReviewIssue> warningIssues = report.getWarningIssues();

                    if (!report.needsFix()) {
                        log.info("代码审查通过 | requestId: {} | 轮次: {}", requestId, attempt + 1);
                        if (progressListener != null) progressListener.accept("代码审查通过，项目质量达标。");
                        workflowService.recordStep(workflowId, reviewAgentId, "代码审查", "自动代码质量检查",
                                buildReviewStepOutput(report, criticalIssues, warningIssues),
                                System.currentTimeMillis() - t0, "completed");
                        // 收集 WARNING 给用户展示
                        if (!warningIssues.isEmpty()) {
                            reviewWarning = buildWarningMessage(warningIssues);
                        }
                        lastReport = null;
                        break;
                    }

                    log.warn("代码审查发现致命问题 | requestId: {} | 轮次: {} | CRITICAL: {} | WARNING: {} | 缺失文件: {}",
                            requestId, attempt + 1, criticalIssues.size(), warningIssues.size(), report.getMissingFiles().size());

                    // 只自动修复 CRITICAL 问题
                    if (!report.getMissingFiles().isEmpty()) {
                        if (progressListener != null) progressListener.accept(
                                String.format("发现 %d 个缺失文件，正在生成...", report.getMissingFiles().size()));
                        projectFiles.putAll(generateMissingFiles(systemName, prdResult.getDocumentContent(),
                                archResult.getDocumentContent(), projectFiles, report.getMissingFiles(), attempt + 1));
                    }

                    if (!criticalIssues.isEmpty()) {
                        if (progressListener != null) progressListener.accept(
                                String.format("发现 %d 个致命问题，正在修复...", criticalIssues.size()));
                        projectFiles.putAll(fixLogicIssues(systemName, prdResult.getDocumentContent(),
                                archResult.getDocumentContent(), projectFiles, criticalIssues, attempt + 1));
                    }

                    workflowService.recordStep(workflowId, reviewAgentId, "代码审查（修复）",
                            String.format("第 %d 轮审查修复（致命:%d，警告:%d，缺失文件:%d）",
                                    attempt + 1, criticalIssues.size(), warningIssues.size(), report.getMissingFiles().size()),
                            buildReviewStepOutput(report, criticalIssues, warningIssues),
                            System.currentTimeMillis() - t0, "completed");

                    attempt++;
                    checkCancellation(cancelCheck, progressListener, requestId);
                }

                if (lastReport != null && lastReport.needsFix()) {
                    log.warn("代码审查已达最大轮次 | requestId: {} | 剩余致命: {} | 警告: {} | 缺失: {}",
                            requestId, lastReport.getCriticalIssues().size(),
                            lastReport.getWarningIssues().size(), lastReport.getMissingFiles().size());
                    StringBuilder sb = new StringBuilder();
                    if (!lastReport.getCriticalIssues().isEmpty()) {
                        sb.append("【需手动确认的问题】已完成 ").append(MAX_FIX_RETRIES).append(" 轮自动修复，以下问题未能完全解决：\n");
                        lastReport.getCriticalIssues().forEach(issue ->
                                sb.append("• [").append(issue.getType()).append("] ").append(issue.getDescription())
                                  .append(" (").append(issue.getFilePath()).append(")\n"));
                    }
                    if (!lastReport.getMissingFiles().isEmpty()) {
                        sb.append("缺失文件：").append(String.join(", ", lastReport.getMissingFiles())).append("\n");
                    }
                    if (!lastReport.getWarningIssues().isEmpty()) {
                        sb.append(buildWarningMessage(lastReport.getWarningIssues()));
                    }
                    reviewWarning = sb.toString();
                    if (progressListener != null) progressListener.accept(reviewWarning);
                }
            } else {
                log.info("代码审查智能体未启用，跳过审查步骤");
            }

            // ── 沙箱部署保障（第二道防线：审查后再次强制确保配置正确） ──
            enforceDeploymentRequirements(projectFiles, systemName);

            if (progressListener != null) progressListener.accept("正在校验项目完整性...");
            workflowService.updateCurrentStep(workflowId, "完整性校验");
            validateProjectIntegrity(projectFiles, systemName, enabledCodes);

            long totalCost = System.currentTimeMillis() - startTime;
            log.info("智能体全流程执行成功（待用户确认保存）| requestId: {} | 总耗时: {}ms | 文件数: {}",
                    requestId, totalCost, projectFiles.size());
            if (progressListener != null) progressListener.accept("项目生成完成，请在预览页面确认后保存。");

            SystemGenerateResult finalResult = SystemGenerateResult.builder()
                    .requestId(requestId)
                    .systemName(systemName)
                    .prdDocumentId(prdResult.getDocumentId())
                    .prdStoragePath(prdResult.getStoragePath())
                    .archDocumentId(archResult.getDocumentId())
                    .archStoragePath(archResult.getStoragePath())
                    .prdContent(prdResult.getDocumentContent())
                    .archContent(archResult.getDocumentContent())
                    .projectFiles(projectFiles)
                    .workflowId(workflowId)
                    .status("success")
                    .errorMsg(reviewWarning)
                    .totalCostMs(totalCost)
                    .build();

            // 保存最终结果到数据库，供后续预览
            workflowService.saveResult(workflowId, finalResult);

            // ★ 持久化产物到 artifact 表，支持历史下载和预览
            try {
                String projectFilesJson = objectMapper.writeValueAsString(projectFiles);
                workflowService.persistArtifacts(requestId, workflowId, systemName,
                        prdResult.getDocumentContent(), archResult.getDocumentContent(), projectFilesJson);
            } catch (Exception e) {
                log.warn("持久化项目文件到 Artifact 表失败 | requestId: {} | err: {}", requestId, e.getMessage());
            }

            return finalResult;

        } catch (TaskCancelledException e) {
            long totalCost = System.currentTimeMillis() - startTime;
            log.info("任务已取消 | requestId: {} | 耗时: {}ms", requestId, totalCost);
            return SystemGenerateResult.builder()
                    .requestId(requestId)
                    .workflowId(workflowId)
                    .status("cancelled")
                    .errorMsg(e.getMessage())
                    .totalCostMs(totalCost)
                    .build();
        } catch (IllegalStateException e) {
            long totalCost = System.currentTimeMillis() - startTime;
            log.error("智能体配置错误 | requestId: {} | 错误: {}", requestId, e.getMessage());
            return buildFailResult(requestId, workflowId, "智能体配置错误：" + e.getMessage(), totalCost);
        } catch (IllegalArgumentException e) {
            long totalCost = System.currentTimeMillis() - startTime;
            log.error("参数解析失败 | requestId: {} | 错误: {}", requestId, e.getMessage());
            return buildFailResult(requestId, workflowId, "输入解析失败：" + e.getMessage(), totalCost);
        } catch (GraphRunnerException e) {
            long totalCost = System.currentTimeMillis() - startTime;
            log.error("智能体执行异常 | requestId: {} | 错误: {}", requestId, e.getMessage(), e);
            return buildFailResult(requestId, workflowId, "智能体执行失败：" + e.getMessage(), totalCost);
        } catch (Exception e) {
            long totalCost = System.currentTimeMillis() - startTime;
            log.error("系统未知异常 | requestId: {} | 错误: {}", requestId, e.getMessage(), e);
            return buildFailResult(requestId, workflowId, "系统异常，请稍后重试：" + e.getMessage(), totalCost);
        }
    }

    private SystemGenerateResult buildFailResult(String requestId, String workflowId, String errorMsg, long totalCost) {
        return SystemGenerateResult.builder()
                .requestId(requestId)
                .workflowId(workflowId)
                .status("fail")
                .errorMsg(errorMsg)
                .totalCostMs(totalCost)
                .build();
    }

    /** 检查取消标志，若已取消则抛出 TaskCancelledException 中止流程 */
    private void checkCancellation(BooleanSupplier cancelCheck, Consumer<String> progressListener, String requestId) {
        if (cancelCheck != null && cancelCheck.getAsBoolean()) {
            log.info("检测到取消信号，中止流程 | requestId: {}", requestId);
            if (progressListener != null) progressListener.accept("正在停止，任务已取消...");
            throw new TaskCancelledException("任务已被用户取消");
        }
    }

    private Map<String, String> generateMissingFiles(String systemName, String prdContent, String archContent,
                                                     Map<String, String> currentFiles, List<String> missingFiles,
                                                     int attempt) throws GraphRunnerException {
        String basePackage = "com." + systemName.toLowerCase().replaceAll("[^a-z0-9]", "");
        StringBuilder prompt = new StringBuilder();
        prompt.append(String.format("你是一个资深全栈工程师。系统「%s」缺少以下文件，请生成它们 (第 %d 次尝试)。\n\n", systemName, attempt));
        prompt.append("### 项目包名: ").append(basePackage).append("\n\n");
        prompt.append("### 需要生成的缺失文件:\n");
        missingFiles.forEach(path -> prompt.append("- ").append(path).append("\n"));
        prompt.append("\n### 相关文件内容（用于理解项目结构和依赖关系）:\n");
        java.util.Set<String> relatedFiles = new java.util.HashSet<>();
        for (String missingPath : missingFiles) {
            if (missingPath.contains("Service.java")) {
                String entityName = missingPath.replaceAll(".*/([A-Z]\\w+)Service\\.java", "$1");
                currentFiles.keySet().stream()
                    .filter(p -> p.contains(entityName + "Controller") || p.contains(entityName + ".java") || p.contains(entityName + "DTO"))
                    .forEach(relatedFiles::add);
            } else if (missingPath.contains("api/") && missingPath.endsWith(".js")) {
                String apiName = missingPath.replaceAll(".*/([a-z]+)\\.js", "$1");
                currentFiles.keySet().stream()
                    .filter(p -> p.toLowerCase().contains(apiName.toLowerCase()) && p.contains("Controller"))
                    .forEach(relatedFiles::add);
            } else if (missingPath.contains("views/") && missingPath.endsWith(".vue")) {
                currentFiles.keySet().stream()
                    .filter(p -> p.contains("router/index") || p.contains("api/"))
                    .forEach(relatedFiles::add);
            }
        }
        relatedFiles.forEach(path -> {
            if (currentFiles.containsKey(path)) {
                prompt.append("\n--- 文件: ").append(path).append(" ---\n").append(currentFiles.get(path)).append("\n");
            }
        });
        prompt.append("\n### PRD 文档（节选）:\n").append(prdContent, 0, Math.min(prdContent.length(), 2000)).append("\n\n");
        prompt.append("### 架构文档（节选）:\n").append(archContent, 0, Math.min(archContent.length(), 2000)).append("\n\n");
        prompt.append("### 生成要求:\n");
        prompt.append("1. 严格按照已有文件的风格和包名生成，确保包名为 ").append(basePackage).append("\n");
        prompt.append("2. 输出格式：[FILE_START] 完整路径 / 完整文件内容 / [FILE_END]\n");
        prompt.append("3. 禁止输出任何解释、注释、Markdown 代码块标签\n");
        Map<String, String> variables = new java.util.LinkedHashMap<>();
        variables.put("project.name", systemName);
        String raw = llmClient.call(systemName, prompt.toString(), variables);
        return fileBlockParser.parse(raw);
    }

    private Map<String, String> fixLogicIssues(String systemName, String prdContent, String archContent,
                                              Map<String, String> currentFiles, List<ReviewIssue> issues,
                                              int attempt) throws GraphRunnerException {
        String basePackage = "com." + systemName.toLowerCase().replaceAll("[^a-z0-9]", "");
        StringBuilder prompt = new StringBuilder();
        prompt.append(String.format("你是一个代码修复专家。系统「%s」存在以下逻辑问题，请修复 (第 %d 次尝试)。\n\n", systemName, attempt));
        prompt.append("### 项目包名: ").append(basePackage).append("\n\n");
        prompt.append("### 发现的问题:\n");
        issues.forEach(issue -> {
            prompt.append(String.format("- **[%s]** %s\n", issue.getType(), issue.getDescription()));
            prompt.append(String.format("  涉及文件: %s\n\n", issue.getFilePath()));
        });
        prompt.append("### 涉及文件的完整内容:\n");
        java.util.Set<String> involvedFiles = new java.util.HashSet<>();
        issues.forEach(issue -> { if (StringUtils.isNotBlank(issue.getFilePath())) involvedFiles.add(issue.getFilePath()); });
        involvedFiles.forEach(path -> {
            if (currentFiles.containsKey(path)) {
                prompt.append("\n--- 文件: ").append(path).append(" ---\n").append(currentFiles.get(path)).append("\n");
            }
        });
        prompt.append("\n### 修复要求:\n");
        prompt.append("1. 只修复有问题的文件，不要修改其他正常文件\n");
        prompt.append("2. 保持包名和路径不变，确保包名为 ").append(basePackage).append("\n");
        prompt.append("3. 修复后的代码必须完整，不要省略任何部分\n");
        prompt.append("4. 输出格式：[FILE_START] 文件路径 / 修复后的完整文件内容 / [FILE_END]\n");
        prompt.append("5. 禁止输出任何解释文字或 Markdown 标签\n");
        Map<String, String> variables = new java.util.LinkedHashMap<>();
        variables.put("project.name", systemName);
        String raw = llmClient.call(systemName, prompt.toString(), variables);
        return fileBlockParser.parse(raw);
    }

    /**
     * 沙箱部署保障：强制覆写前端配置，确保 E2B 沙箱能正确运行项目。
     * 无论 LLM 生成了什么内容，此方法最终保证：
     * 1. vite.config.js — 正确的 host/port/allowedHosts/proxy
     * 2. package.json  — 正确的 scripts 和必要依赖
     * 3. index.html    — 存在且包含 id="app" 挂载点
     */
    @SuppressWarnings("unchecked")
    private void enforceDeploymentRequirements(Map<String, String> projectFiles, String systemName) {
        // 1. 强制覆写 vite.config.js
        // allowedHosts 必须是布尔值 true，而非字符串 'all'。
        // Vite 5.4+ 的类型定义为 string[] | true，字符串 'all' 不会被识别为"允许全部"，
        // 只有 true 才能完全禁用主机检查，使 E2B 沙箱域名（*.e2b.dev）得以访问。
        String viteConfig = "import { defineConfig } from 'vite'\n"
                + "import vue from '@vitejs/plugin-vue'\n\n"
                + "export default defineConfig({\n"
                + "  plugins: [vue()],\n"
                + "  server: {\n"
                + "    host: '0.0.0.0',\n"
                + "    port: 5173,\n"
                + "    allowedHosts: true,\n"
                + "    proxy: {\n"
                + "      '/api': {\n"
                + "        target: 'http://localhost:8080',\n"
                + "        changeOrigin: true\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "})\n";
        projectFiles.put("frontend/vite.config.js", viteConfig);

        // 2. 修补 package.json：保留 LLM 生成的内容，仅强制覆盖关键字段
        String pkgKey = "frontend/package.json";
        try {
            Map<String, Object> pkg;
            if (projectFiles.containsKey(pkgKey)) {
                pkg = objectMapper.readValue(projectFiles.get(pkgKey),
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.LinkedHashMap<String, Object>>() {});
            } else {
                pkg = new java.util.LinkedHashMap<>();
            }
            pkg.putIfAbsent("name", systemName.toLowerCase().replaceAll("[^a-z0-9]", "-"));
            pkg.putIfAbsent("version", "0.0.1");
            pkg.put("type", "module");  // ESModule，Vite 要求

            // scripts 必须完整
            Map<String, Object> scripts = (Map<String, Object>) pkg.computeIfAbsent(
                    "scripts", k -> new java.util.LinkedHashMap<>());
            scripts.put("dev", "vite");
            scripts.put("build", "vite build");
            scripts.put("preview", "vite preview");

            // dependencies：仅补充缺失，不覆盖 LLM 写的版本
            Map<String, Object> deps = (Map<String, Object>) pkg.computeIfAbsent(
                    "dependencies", k -> new java.util.LinkedHashMap<>());
            deps.putIfAbsent("vue", "^3.4.0");
            deps.putIfAbsent("vue-router", "^4.3.0");
            deps.putIfAbsent("pinia", "^2.1.0");
            deps.putIfAbsent("axios", "^1.6.0");
            deps.putIfAbsent("element-plus", "^2.7.0");

            // devDependencies：vite 和 plugin-vue 必须存在
            Map<String, Object> devDeps = (Map<String, Object>) pkg.computeIfAbsent(
                    "devDependencies", k -> new java.util.LinkedHashMap<>());
            devDeps.putIfAbsent("vite", "^5.2.0");
            devDeps.putIfAbsent("@vitejs/plugin-vue", "^5.0.0");

            projectFiles.put(pkgKey, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(pkg));
        } catch (Exception e) {
            log.warn("修补 package.json 失败，降级为强制覆写 | err: {}", e.getMessage());
            String safeName = systemName.toLowerCase().replaceAll("[^a-z0-9]", "-");
            projectFiles.put(pkgKey, "{\n"
                    + "  \"name\": \"" + safeName + "\",\n"
                    + "  \"version\": \"0.0.1\",\n"
                    + "  \"type\": \"module\",\n"
                    + "  \"scripts\": {\n"
                    + "    \"dev\": \"vite\",\n"
                    + "    \"build\": \"vite build\",\n"
                    + "    \"preview\": \"vite preview\"\n"
                    + "  },\n"
                    + "  \"dependencies\": {\n"
                    + "    \"vue\": \"^3.4.0\",\n"
                    + "    \"vue-router\": \"^4.3.0\",\n"
                    + "    \"pinia\": \"^2.1.0\",\n"
                    + "    \"axios\": \"^1.6.0\",\n"
                    + "    \"element-plus\": \"^2.7.0\"\n"
                    + "  },\n"
                    + "  \"devDependencies\": {\n"
                    + "    \"vite\": \"^5.2.0\",\n"
                    + "    \"@vitejs/plugin-vue\": \"^5.0.0\"\n"
                    + "  }\n"
                    + "}\n");
        }

        // 3. 确保 index.html 存在，且包含正确的挂载点
        if (!projectFiles.containsKey("frontend/index.html")) {
            projectFiles.put("frontend/index.html", "<!DOCTYPE html>\n"
                    + "<html lang=\"zh\">\n"
                    + "  <head>\n"
                    + "    <meta charset=\"UTF-8\" />\n"
                    + "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />\n"
                    + "    <title>" + systemName + "</title>\n"
                    + "  </head>\n"
                    + "  <body>\n"
                    + "    <div id=\"app\"></div>\n"
                    + "    <script type=\"module\" src=\"/src/main.js\"></script>\n"
                    + "  </body>\n"
                    + "</html>\n");
            log.info("已补全 frontend/index.html（沙箱部署保障）");
        }

        log.info("沙箱部署保障已执行 | 系统: {} | 已写入 vite.config.js / package.json / index.html", systemName);
    }

    private void validateProjectIntegrity(Map<String, String> projectFiles, String systemName,
                                          Set<String> enabledCodes) throws GraphRunnerException {
        String basePackage = "com." + systemName.toLowerCase().replaceAll("[^a-z0-9]", "");
        boolean backendEnabled = enabledCodes.contains(AgentDefinition.BACKEND_SKELETON.getAgentCode());
        boolean frontendEnabled = enabledCodes.contains(AgentDefinition.FRONTEND_SKELETON.getAgentCode());

        if (frontendEnabled) {
            if (!projectFiles.containsKey("frontend/src/main.js") && !projectFiles.containsKey("frontend/src/main.ts")) {
                throw new GraphRunnerException("前端骨架缺少必需文件: src/main.js 或 src/main.ts");
            }
            if (!projectFiles.containsKey("frontend/src/App.vue")) {
                throw new GraphRunnerException("前端骨架缺少必需文件: src/App.vue");
            }
            // 沙箱部署必需文件校验（enforceDeploymentRequirements 已保障，此处为最终安全网）
            if (!projectFiles.containsKey("frontend/vite.config.js")) {
                throw new GraphRunnerException("前端缺少沙箱部署必需文件: vite.config.js");
            }
            if (!projectFiles.containsKey("frontend/package.json")) {
                throw new GraphRunnerException("前端缺少沙箱部署必需文件: package.json");
            }
            if (!projectFiles.containsKey("frontend/index.html")) {
                throw new GraphRunnerException("前端缺少沙箱部署必需文件: index.html");
            }
            // 校验 vite.config.js 包含沙箱运行所需的关键配置
            String viteConf = projectFiles.get("frontend/vite.config.js");
            // 检查 allowedHosts: true（布尔值）和 host: '0.0.0.0' 是否存在
            if (!viteConf.contains("allowedHosts: true") || !viteConf.contains("0.0.0.0")) {
                log.warn("vite.config.js 缺少沙箱配置（需要 allowedHosts: true），将重新强制写入");
                enforceDeploymentRequirements(projectFiles, systemName);
            }
        }

        if (backendEnabled) {
            if (!projectFiles.containsKey("backend/pom.xml")) {
                throw new GraphRunnerException("后端骨架缺少必需文件: pom.xml");
            }
            boolean hasAuthService = projectFiles.keySet().stream().anyMatch(p -> p.contains("AuthService.java"));
            if (!hasAuthService) {
                throw new GraphRunnerException("后端领域层缺少必需组件: AuthService.java");
            }
            boolean isExamplePackage = projectFiles.values().stream().anyMatch(v -> v.contains("package com.example"));
            if (isExamplePackage) {
                throw new GraphRunnerException("后端文件包名错误：检测到 com.example 包名，而非 " + basePackage);
            }
        }
    }

    private String buildWarningMessage(List<ReviewIssue> warnings) {
        StringBuilder sb = new StringBuilder("【代码审查警告（不影响运行）】\n");
        warnings.forEach(w ->
                sb.append("• [").append(w.getType()).append("] ").append(w.getDescription())
                  .append(" (").append(w.getFilePath()).append(")\n"));
        return sb.toString();
    }

    private String buildReviewStepOutput(CodeReviewReport report, List<ReviewIssue> critical, List<ReviewIssue> warnings) {
        StringBuilder sb = new StringBuilder();
        sb.append("总结: ").append(report.getSummary()).append("\n");
        sb.append("致命问题: ").append(critical.size()).append(" 个  |  警告: ").append(warnings.size()).append(" 个\n");
        if (!critical.isEmpty()) {
            sb.append("=== CRITICAL ===\n");
            critical.forEach(i -> sb.append("• [").append(i.getType()).append("] ").append(i.getDescription())
                    .append(" → ").append(i.getFilePath()).append("\n"));
        }
        if (!warnings.isEmpty()) {
            sb.append("=== WARNING ===\n");
            warnings.forEach(i -> sb.append("• [").append(i.getType()).append("] ").append(i.getDescription())
                    .append(" → ").append(i.getFilePath()).append("\n"));
        }
        return sb.toString();
    }

    private void validateUserInput(String userInput) {
        if (StringUtils.isBlank(userInput)) {
            throw new IllegalArgumentException("用户输入不能为空，请输入需要生成的系统需求");
        }
        if (userInput.length() > MAX_INPUT_LENGTH) {
            throw new IllegalArgumentException("输入内容过长（最大支持" + MAX_INPUT_LENGTH + "个字符），请简化描述");
        }
        if (ILLEGAL_CHAR_PATTERN.matcher(userInput).find()) {
            throw new IllegalArgumentException("输入包含非法字符（\\/:*?\"<>|），请删除后重试");
        }
    }

    private String extractSystemName(String userInput) {
        for (Pattern pattern : SYSTEM_NAME_PATTERNS) {
            Matcher matcher = pattern.matcher(userInput);
            if (matcher.find()) {
                String systemName = matcher.group(1).trim();
                if (StringUtils.isNotBlank(systemName)) {
                    String cleanName = cleanSystemName(systemName);
                    if (StringUtils.isNotBlank(cleanName)) return cleanName;
                }
            }
        }
        if (userInput.length() < 20) {
            String cleanName = cleanSystemName(userInput);
            if (StringUtils.isNotBlank(cleanName)) return cleanName;
        }
        String[] suffixes = {"系统", "平台", "APP", "小程序", "网站", "项目"};
        for (String suffix : suffixes) {
            if (userInput.contains(suffix)) {
                int index = userInput.indexOf(suffix);
                int start = Math.max(0, index - 15);
                String sub = userInput.substring(start, index + suffix.length());
                String cleanName = cleanSystemName(sub);
                if (StringUtils.isNotBlank(cleanName)) return cleanName;
            }
        }
        throw new IllegalArgumentException(
                "未识别到有效系统名称！请在描述中包含「XX系统/平台/项目」等关键词，或直接输入系统名称。");
    }

    private String cleanSystemName(String name) {
        if (StringUtils.isBlank(name)) return "";
        String clean = name.replaceAll("^(构建|开发|编写|创建|生成|做一个|搞一个|帮我|为我|一个|的)+", "");
        clean = ILLEGAL_CHAR_PATTERN.matcher(clean).replaceAll("");
        return clean.trim();
    }

    private String generateShortRequestId() {
        long timestampPart = System.currentTimeMillis() % 1000000L;
        int randomPart = (int) (Math.random() * 1000);
        return timestampPart + "" + randomPart;
    }

    private String maskSensitiveInput(String input) {
        if (StringUtils.isBlank(input)) return "";
        return input.length() > 100 ? input.substring(0, 100) + "..." : input;
    }
}

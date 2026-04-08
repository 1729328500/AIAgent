package top.whyh.agentai.coordinator;

import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
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
import top.whyh.agentai.service.codegen.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
            Pattern.compile("(为我|帮我|我要)?(编写|开发|创建|生成)一个(.*?)系统", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(.*?)系统(的PRD和架构文档|的需求和架构|开发)", Pattern.CASE_INSENSITIVE)
    );
    private static final int MAX_INPUT_LENGTH = 500;
    private static final Pattern ILLEGAL_CHAR_PATTERN = Pattern.compile("[\\\\/:*?\"<>|]");
    private static final int MAX_FIX_RETRIES = 10;

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

    public SystemGenerateResult executeFullFlow(String userInput) {
        return executeFullFlow(userInput, null);
    }

    /**
     * 执行全流程，返回结果中包含 projectFiles（不自动落盘，由调用方决定是否保存）
     */
    public SystemGenerateResult executeFullFlow(String userInput, Consumer<String> progressListener) {
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

            // 需求分析（核心，必须启用）
            if (progressListener != null) progressListener.accept("正在生成 PRD 文档...");
            long prdStart = System.currentTimeMillis();
            RequirementResult prdResult = requirementService.generateRequirementDocument(userInput);
            log.info("PRD文档生成完成 | requestId: {} | 耗时: {}ms", requestId, System.currentTimeMillis() - prdStart);

            // 架构设计（核心，必须启用）
            if (progressListener != null) progressListener.accept("正在生成架构文档...");
            long archStart = System.currentTimeMillis();
            ArchitectResult archResult = architectService.generateArchitectDocument(
                    prdResult.getDocumentContent(), systemName);
            log.info("架构文档生成完成 | requestId: {} | 耗时: {}ms", requestId, System.currentTimeMillis() - archStart);

            // ========== 动态编排：根据启用状态决定是否执行各代码生成步骤 ==========
            Map<String, String> projectFiles = new java.util.LinkedHashMap<>();

            if (agentRegistry.canExecute(AgentDefinition.BACKEND_SKELETON, enabledCodes)) {
                if (progressListener != null) progressListener.accept("正在生成后端项目骨架...");
                projectFiles.putAll(backendSkeletonGenerator.generateBackendSkeleton(
                        systemName, prdResult.getDocumentContent(), archResult.getDocumentContent()));
            }

            if (agentRegistry.canExecute(AgentDefinition.BACKEND_CONTROLLER, enabledCodes)) {
                if (progressListener != null) progressListener.accept("正在生成后端接口 (Controller)...");
                projectFiles.putAll(backendControllerGenerator.generateBackendControllers(
                        systemName, prdResult.getDocumentContent(), archResult.getDocumentContent()));
            }

            if (agentRegistry.canExecute(AgentDefinition.BACKEND_DOMAIN, enabledCodes)) {
                if (progressListener != null) progressListener.accept("正在生成后端领域层 (Service/Entity/DTO)...");
                projectFiles.putAll(backendDomainGenerator.generateBackendDomainLayer(
                        systemName, prdResult.getDocumentContent(), archResult.getDocumentContent()));
            }

            if (agentRegistry.canExecute(AgentDefinition.FRONTEND_SKELETON, enabledCodes)) {
                if (progressListener != null) progressListener.accept("正在生成前端项目骨架...");
                projectFiles.putAll(frontendSkeletonGenerator.generateFrontendSkeleton(
                        systemName, prdResult.getDocumentContent(), archResult.getDocumentContent()));
            }

            if (agentRegistry.canExecute(AgentDefinition.FRONTEND_VIEWS, enabledCodes)) {
                if (progressListener != null) progressListener.accept("正在生成前端页面 (Views)...");
                projectFiles.putAll(frontendViewsGenerator.generateFrontendViews(
                        systemName, prdResult.getDocumentContent(), archResult.getDocumentContent(), projectFiles));
            }

            if (agentRegistry.canExecute(AgentDefinition.FRONTEND_API, enabledCodes)) {
                if (progressListener != null) progressListener.accept("正在封装前端 API 接口...");
                projectFiles.putAll(frontendApiGenerator.generateFrontendApiUtils(
                        systemName, prdResult.getDocumentContent(), archResult.getDocumentContent(), projectFiles));
            }

            log.info("项目代码合并完成 | requestId: {} | 文件总数: {}", requestId, projectFiles.size());

            // ========== 代码审查与修复循环 ==========
            if (agentRegistry.canExecute(AgentDefinition.CODE_REVIEW, enabledCodes)) {
                int attempt = 0;
                while (attempt < MAX_FIX_RETRIES) {
                    if (progressListener != null) progressListener.accept(String.format("正在进行代码审查 (第%d轮)...", attempt + 1));
                    CodeReviewReport report = codeReviewService.reviewCode(
                            systemName, prdResult.getDocumentContent(), archResult.getDocumentContent(), projectFiles);

                    if (!report.needsFix()) {
                        log.info("代码审查通过 | requestId: {} | 轮次: {}", requestId, attempt + 1);
                        if (progressListener != null) progressListener.accept("代码审查通过，项目质量达标。");
                        break;
                    }

                    log.warn("代码审查未通过 | requestId: {} | 轮次: {} | 问题数: {} | 缺失文件数: {}",
                            requestId, attempt + 1, report.getIssues().size(), report.getMissingFiles().size());

                    if (!report.getMissingFiles().isEmpty()) {
                        if (progressListener != null) progressListener.accept(
                                String.format("发现 %d 个缺失文件，正在生成...", report.getMissingFiles().size()));
                        projectFiles.putAll(generateMissingFiles(systemName, prdResult.getDocumentContent(),
                                archResult.getDocumentContent(), projectFiles, report.getMissingFiles(), attempt + 1));
                    }

                    if (!report.getIssues().isEmpty()) {
                        if (progressListener != null) progressListener.accept(
                                String.format("发现 %d 个逻辑问题，正在修复...", report.getIssues().size()));
                        projectFiles.putAll(fixLogicIssues(systemName, prdResult.getDocumentContent(),
                                archResult.getDocumentContent(), projectFiles, report.getIssues(), attempt + 1));
                    }

                    attempt++;
                    if (attempt == MAX_FIX_RETRIES) {
                        log.warn("已达到最大修复重试次数 | requestId: {}", requestId);
                        if (progressListener != null) progressListener.accept("警告：已达到最大修复次数，部分问题可能仍未解决。");
                    }
                }
            } else {
                log.info("代码审查智能体未启用，跳过审查步骤");
            }

            if (progressListener != null) progressListener.accept("正在校验项目完整性...");
            validateProjectIntegrity(projectFiles, systemName, enabledCodes);

            long totalCost = System.currentTimeMillis() - startTime;
            log.info("智能体全流程执行成功（待用户确认保存）| requestId: {} | 总耗时: {}ms | 文件数: {}",
                    requestId, totalCost, projectFiles.size());
            if (progressListener != null) progressListener.accept("项目生成完成，请在预览页面确认后保存。");
            return new SystemGenerateResult(requestId, systemName,
                    prdResult.getDocumentId(), prdResult.getStoragePath(),
                    archResult.getDocumentId(), archResult.getStoragePath(),
                    projectFiles, "success", "", totalCost);

        } catch (IllegalStateException e) {
            long totalCost = System.currentTimeMillis() - startTime;
            log.error("智能体配置错误 | requestId: {} | 错误: {}", requestId, e.getMessage());
            return buildFailResult(requestId, "智能体配置错误：" + e.getMessage(), totalCost);
        } catch (IllegalArgumentException e) {
            long totalCost = System.currentTimeMillis() - startTime;
            log.error("参数解析失败 | requestId: {} | 错误: {}", requestId, e.getMessage());
            return buildFailResult(requestId, "输入解析失败：" + e.getMessage(), totalCost);
        } catch (GraphRunnerException e) {
            long totalCost = System.currentTimeMillis() - startTime;
            log.error("智能体执行异常 | requestId: {} | 错误: {}", requestId, e.getMessage(), e);
            return buildFailResult(requestId, "智能体执行失败：" + e.getMessage(), totalCost);
        } catch (Exception e) {
            long totalCost = System.currentTimeMillis() - startTime;
            log.error("系统未知异常 | requestId: {} | 错误: {}", requestId, e.getMessage(), e);
            return buildFailResult(requestId, "系统异常，请稍后重试：" + e.getMessage(), totalCost);
        }
    }

    private SystemGenerateResult buildFailResult(String requestId, String errorMsg, long totalCost) {
        return new SystemGenerateResult(requestId, "", "", "", "", "", null, "fail", errorMsg, totalCost);
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
                String systemName = null;
                for (int i = matcher.groupCount(); i >= 1; i--) {
                    String group = matcher.group(i);
                    if (StringUtils.isNotBlank(group) && !group.matches("(为我|帮我|我要|编写|开发|创建|的|和|PRD|架构|需求|文档|开发)")) {
                        systemName = group.trim();
                        break;
                    }
                }
                if (StringUtils.isBlank(systemName)) continue;
                String cleanSystemName = ILLEGAL_CHAR_PATTERN.matcher(systemName).replaceAll("");
                if (StringUtils.isBlank(cleanSystemName)) {
                    throw new IllegalArgumentException("系统名称包含非法字符，无法识别");
                }
                return cleanSystemName;
            }
        }
        throw new IllegalArgumentException(
                "未识别到有效系统名称！请按示例格式输入：\n" +
                "1. 为我编写一个电商订单系统\n" +
                "2. 帮我开发一个物流管理系统\n" +
                "3. 生成库存管理系统的PRD和架构文档"
        );
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
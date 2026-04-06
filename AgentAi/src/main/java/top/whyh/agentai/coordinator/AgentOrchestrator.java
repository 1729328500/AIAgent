package top.whyh.agentai.coordinator;

import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Component;
import top.whyh.agentai.result.ArchitectResult;
import top.whyh.agentai.result.RequirementResult;
import top.whyh.agentai.result.SystemGenerateResult;
import top.whyh.agentai.service.ArchitectAnalysisService;
import top.whyh.agentai.service.CodeGenerationService;
import top.whyh.agentai.service.CodeOutputService;
import top.whyh.agentai.service.FrontendGenerationService;
import top.whyh.agentai.service.RequirementAnalysisService;
import top.whyh.agentai.model.dto.CodeReviewReport;
import top.whyh.agentai.model.dto.ReviewIssue;
import top.whyh.agentai.service.CodeReviewService;
import top.whyh.agentai.service.codegen.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 智能体协调器：串联需求分析智能体和架构师智能体
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentOrchestrator {
    // ========== 常量抽离：便于维护和配置 ==========
    /** 支持的系统名称匹配正则（放宽输入格式，支持多句式） */
    private static final List<Pattern> SYSTEM_NAME_PATTERNS = List.of(
            Pattern.compile("(为我|帮我|我要)?(编写|开发|创建|生成)一个(.*?)系统", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(.*?)系统(的PRD和架构文档|的需求和架构|开发)", Pattern.CASE_INSENSITIVE)
    );
    /** 输入长度上限（防止恶意超长输入） */
    private static final int MAX_INPUT_LENGTH = 500;
    /** 系统名称非法字符正则 */
    private static final Pattern ILLEGAL_CHAR_PATTERN = Pattern.compile("[\\\\/:*?\"<>|]");
    /** 最大修复重试次数 */
    private static final int MAX_FIX_RETRIES = 10;

    private final RequirementAnalysisService requirementService;
    private final ArchitectAnalysisService architectService;
    private final CodeGenerationService codeGenerationService;
    private final CodeOutputService codeOutputService;
    private final FrontendGenerationService frontendGenerationService;
    private final BackendSkeletonGenerator backendSkeletonGenerator;
    private final BackendControllerGenerator backendControllerGenerator;
    private final BackendDomainGenerator backendDomainGenerator;
    private final FrontendSkeletonGenerator frontendSkeletonGenerator;
    private final FrontendViewsGenerator frontendViewsGenerator;
    private final FrontendApiGenerator frontendApiGenerator;
    private final CodeReviewService codeReviewService; // ← 新增
    private final LlmClient llmClient; // ← 新增，用于直接修复
    private final FileBlockParser fileBlockParser; // ← 新增


    /**
     * 执行全流程：解析输入 → 生成PRD → 生成架构文档
     * @param userInput 用户输入（如：为我编写一个电商订单系统）
     * @return 全流程结果
     */
    /**
     * 执行全流程：解析输入 → 生成PRD → 生成架构文档 → 生成完整项目
     */
    public SystemGenerateResult executeFullFlow(String userInput) {
        return executeFullFlow(userInput, null);
    }

    /**
     * 执行全流程，支持进度回调
     */
    public SystemGenerateResult executeFullFlow(String userInput, Consumer<String> progressListener) {
        long startTime = System.currentTimeMillis();
        String requestId = generateShortRequestId();
        log.info("开始执行智能体全流程 | requestId: {} | 用户输入: {}", requestId, maskSensitiveInput(userInput));

        try {
            // 1-2. 输入校验 & 提取系统名 (保持不变)
            validateUserInput(userInput);
            String systemName = extractSystemName(userInput);
            log.info("解析到有效系统名称 | requestId: {} | 系统名称: {}", requestId, systemName);
            if (progressListener != null) progressListener.accept("解析到有效系统名称：" + systemName);

            // 3. 生成PRD (保持不变)
            log.info("开始生成PRD文档 | requestId: {} | 系统名称: {}", requestId, systemName);
            if (progressListener != null) progressListener.accept("正在生成 PRD 文档...");
            long prdStart = System.currentTimeMillis();
            RequirementResult prdResult = requirementService.generateRequirementDocument(userInput);
            long prdCost = System.currentTimeMillis() - prdStart;
            log.info("PRD文档生成完成 | requestId: {} | 耗时: {}ms | 存储路径: {}", requestId, prdCost, prdResult.getStoragePath());

            // 4. 生成架构文档 (保持不变)
            log.info("开始生成架构文档 | requestId: {} | 系统名称: {}", requestId, systemName);
            if (progressListener != null) progressListener.accept("正在生成架构文档...");
            long archStart = System.currentTimeMillis();
            ArchitectResult archResult = architectService.generateArchitectDocument(
                    prdResult.getDocumentContent(), systemName);
            long archCost = System.currentTimeMillis() - archStart;
            log.info("架构文档生成完成 | requestId: {} | 耗时: {}ms | 存储路径: {}", requestId, archCost, archResult.getStoragePath());

            // ========== 关键修改区域开始：后端 → 前端 两阶段 ==========
            // 5. 后端骨架
            if (progressListener != null) progressListener.accept("正在生成后端项目骨架...");
            Map<String, String> projectFiles = new java.util.LinkedHashMap<>();
            Map<String, String> backendSkeleton = backendSkeletonGenerator.generateBackendSkeleton(
                    systemName, prdResult.getDocumentContent(), archResult.getDocumentContent());
            projectFiles.putAll(backendSkeleton);

            // 6. 后端 Controller
            if (progressListener != null) progressListener.accept("正在生成后端接口 (Controller)...");
            Map<String, String> backendControllers = backendControllerGenerator.generateBackendControllers(
                    systemName, prdResult.getDocumentContent(), archResult.getDocumentContent());
            projectFiles.putAll(backendControllers);

            // 7. 后端 Domain
            if (progressListener != null) progressListener.accept("正在生成后端领域层 (Service/Entity/DTO)...");
            Map<String, String> backendDomain = backendDomainGenerator.generateBackendDomainLayer(
                    systemName, prdResult.getDocumentContent(), archResult.getDocumentContent());
            projectFiles.putAll(backendDomain);

            // 8. 前端骨架
            if (progressListener != null) progressListener.accept("正在生成前端项目骨架...");
            Map<String, String> frontendSkeleton = frontendSkeletonGenerator.generateFrontendSkeleton(
                    systemName, prdResult.getDocumentContent(), archResult.getDocumentContent());
            projectFiles.putAll(frontendSkeleton);

            // 9. 前端 Views
            if (progressListener != null) progressListener.accept("正在生成前端页面 (Views)...");
            Map<String, String> frontendViews = frontendViewsGenerator.generateFrontendViews(
                    systemName, prdResult.getDocumentContent(), archResult.getDocumentContent(), projectFiles);
            projectFiles.putAll(frontendViews);

            // 10. 前端 API 封装
            if (progressListener != null) progressListener.accept("正在封装前端 API 接口...");
            Map<String, String> frontendApi = frontendApiGenerator.generateFrontendApiUtils(
                    systemName, prdResult.getDocumentContent(), archResult.getDocumentContent(), projectFiles);
            projectFiles.putAll(frontendApi);

            log.info("项目代码合并完成 | requestId: {} | 文件总数: {}", requestId, projectFiles.size());

            // ========== 11. 代码审查与修复循环 ==========
            int attempt = 0;
            while (attempt < MAX_FIX_RETRIES) {
                if (progressListener != null) progressListener.accept(String.format("正在进行代码审查 (第%d轮)...", attempt + 1));
                CodeReviewReport report = codeReviewService.reviewCode(systemName, prdResult.getDocumentContent(), archResult.getDocumentContent(), projectFiles);
                
                if (!report.needsFix()) {
                    log.info("代码审查通过 | requestId: {} | 轮次: {}", requestId, attempt + 1);
                    if (progressListener != null) progressListener.accept("代码审查通过，项目质量达标。");
                    break;
                }
                
                log.warn("代码审查未通过 | requestId: {} | 轮次: {} | 问题数: {} | 缺失文件数: {}", 
                        requestId, attempt + 1, report.getIssues().size(), report.getMissingFiles().size());
                
                if (progressListener != null) {
                    String status = String.format("审查发现问题：%d个问题，%d个缺失文件。正在尝试修复...", 
                            report.getIssues().size(), report.getMissingFiles().size());
                    progressListener.accept(status);
                }
                
                // 执行修复
                Map<String, String> fixedFiles = fixProjectFiles(systemName, prdResult.getDocumentContent(), archResult.getDocumentContent(), projectFiles, report, attempt + 1);
                projectFiles.putAll(fixedFiles);
                
                attempt++;
                if (attempt == MAX_FIX_RETRIES) {
                    log.warn("已达到最大修复重试次数，将输出当前状态的项目 | requestId: {}", requestId);
                    if (progressListener != null) progressListener.accept("警告：已达到最大修复次数，部分问题可能仍未解决。");
                }
            }

            // 6. 保存整个项目到 D 盘指定目录
            if (progressListener != null) progressListener.accept("正在落盘保存完整项目文件...");
            validateProjectIntegrity(projectFiles, systemName);
            String projectRootPath = codeOutputService.saveGeneratedProject(systemName, projectFiles);
            log.info("✅ 完整项目已保存至: {}", projectRootPath);
            // ========== 关键修改区域结束 ==========

            // 7. 封装成功结果
            long totalCost = System.currentTimeMillis() - startTime;
            log.info("智能体全流程执行成功 | requestId: {} | 总耗时: {}ms", requestId, totalCost);
            return new SystemGenerateResult(
                    requestId,
                    systemName,
                    prdResult.getDocumentId(),
                    prdResult.getStoragePath(),
                    archResult.getDocumentId(),
                    archResult.getStoragePath(),
                    projectRootPath, // 👈 这里传入的是项目根目录路径
                    "success",
                    "",
                    totalCost
            );

        } catch (IllegalArgumentException e) {
            long totalCost = System.currentTimeMillis() - startTime;
            log.error("参数解析失败 | requestId: {} | 耗时: {}ms | 错误: {}", requestId, totalCost, e.getMessage());
            return new SystemGenerateResult(
                    requestId,
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "fail",
                    "输入解析失败：" + e.getMessage(),
                    totalCost
            );
        } catch (GraphRunnerException e) {
            long totalCost = System.currentTimeMillis() - startTime;
            log.error("智能体执行异常 | requestId: {} | 耗时: {}ms | 错误: {}", requestId, totalCost, e.getMessage(), e);
            return new SystemGenerateResult(
                    requestId,
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "fail",
                    "智能体执行失败：" + e.getMessage(),
                    totalCost
            );
        } catch (Exception e) {
            long totalCost = System.currentTimeMillis() - startTime;
            log.error("系统未知异常 | requestId: {} | 耗时: {}ms | 错误: {}", requestId, totalCost, e.getMessage(), e);
            return new SystemGenerateResult(
                    requestId,
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "fail",
                    "系统异常，请稍后重试：" + e.getMessage(),
                    totalCost
            );
        }
    }

    /**
     * 根据审查报告修复项目文件
     */
    private Map<String, String> fixProjectFiles(String systemName, String prdContent, String archContent, 
                                               Map<String, String> currentFiles, CodeReviewReport report, int attempt) throws GraphRunnerException {
        String basePackage = "com." + systemName.toLowerCase().replaceAll("[^a-z0-9]", "");
        
        StringBuilder fixPrompt = new StringBuilder();
        fixPrompt.append(String.format("你是一个资深全栈修复工程师。正在修复系统「%s」的代码生成问题 (第 %d 次修复尝试)。\n", systemName, attempt));
        fixPrompt.append("### 修复上下文:\n");
        fixPrompt.append("- **项目包名**: ").append(basePackage).append("\n");
        fixPrompt.append("- **已存在的文件列表**:\n");
        currentFiles.keySet().forEach(path -> fixPrompt.append("  - ").append(path).append("\n"));
        
        // 【关键优化】提取涉及文件的内容，提供给 AI 作为修复参考
        fixPrompt.append("\n### 涉及文件的当前内容:\n");
        java.util.Set<String> relatedFiles = new java.util.HashSet<>();
        report.getIssues().forEach(issue -> {
            if (StringUtils.isNotBlank(issue.getFilePath())) {
                relatedFiles.add(issue.getFilePath());
            }
        });
        
        relatedFiles.forEach(path -> {
            if (currentFiles.containsKey(path)) {
                fixPrompt.append("--- 文件: ").append(path).append(" ---\n");
                fixPrompt.append(currentFiles.get(path)).append("\n");
            }
        });

        fixPrompt.append("\n### 审查发现的问题:\n");
        report.getIssues().forEach(issue -> 
            fixPrompt.append(String.format("- [%s] %s (涉及文件: %s)\n", issue.getType(), issue.getDescription(), issue.getFilePath()))
        );
        
        if (!report.getMissingFiles().isEmpty()) {
            fixPrompt.append("\n### 缺失文件列表 (请生成这些文件):\n");
            report.getMissingFiles().forEach(path -> fixPrompt.append("- ").append(path).append("\n"));
        }

        fixPrompt.append("\n### 任务要求:\n");
        fixPrompt.append("1. **精准修复**：优先修复逻辑错误，并补全缺失的文件。\n");
        fixPrompt.append("2. **禁止破坏**：修复过程中严禁破坏已有的正确逻辑，确保新代码与现有项目包名、API 路径、依赖关系完美契合。\n");
        fixPrompt.append("3. **参考架构**：如果修复涉及接口调用，请务必参考 PRD 和架构文档，确保前后端参数名、路径完全一致。\n");
        fixPrompt.append("4. **严格格式**：每个文件必须按以下结构输出，严禁遗漏内容：\n");
        fixPrompt.append("   [FILE_START] 路径\n");
        fixPrompt.append("   内容...\n");
        fixPrompt.append("   [FILE_END]\n");
        fixPrompt.append("5. **前缀强制**：所有路径必须以 backend/ 或 frontend/ 开头。\n");
        fixPrompt.append("不要输出任何解释文字或 Markdown 代码块标签。\n");

        Map<String, String> variables = new java.util.LinkedHashMap<>();
        variables.put("project.name", systemName);
        variables.put("review.summary", report.getSummary());

        String raw = llmClient.call(systemName, fixPrompt.toString(), variables);
        Map<String, String> fixedFiles = fileBlockParser.parse(raw);
        
        log.info("修复逻辑执行完成 | 轮次: {} | 修复/补充文件数: {}", attempt, fixedFiles.size());
        return fixedFiles;
    }

    /**
     * 最终项目完整性校验
     */
    private void validateProjectIntegrity(Map<String, String> projectFiles, String systemName) throws GraphRunnerException {
        String basePackage = "com." + systemName.toLowerCase().replaceAll("[^a-z0-9]", "");
        
        // 1. 前端必需文件
        if (!projectFiles.containsKey("frontend/src/main.js") && !projectFiles.containsKey("frontend/src/main.ts")) {
            throw new GraphRunnerException("前端骨架缺少必需文件: src/main.js 或 src/main.ts");
        }
        if (!projectFiles.containsKey("frontend/src/App.vue")) {
            throw new GraphRunnerException("前端骨架缺少必需文件: src/App.vue");
        }
        if (!projectFiles.containsKey("frontend/src/router/index.js") && !projectFiles.containsKey("frontend/src/router/index.ts")) {
            throw new GraphRunnerException("前端骨架缺少必需文件: src/router/index.js 或 index.ts");
        }
        if (!projectFiles.containsKey("frontend/src/utils/axios.js") && !projectFiles.containsKey("frontend/src/utils/axios.ts")) {
            throw new GraphRunnerException("前端骨架缺少必需文件: src/utils/axios.js 或 axios.ts");
        }

        // 2. 后端必需文件
        if (!projectFiles.containsKey("backend/pom.xml")) {
            throw new GraphRunnerException("后端骨架缺少必需文件: pom.xml");
        }
        
        // 3. 业务完整性检查
        boolean hasAuthService = projectFiles.keySet().stream().anyMatch(p -> p.contains("AuthService.java"));
        if (!hasAuthService) {
            throw new GraphRunnerException("后端领域层缺少必需组件: AuthService.java");
        }
        
        boolean hasSecurityConfig = projectFiles.keySet().stream().anyMatch(p -> p.contains("SecurityConfig.java"));
        if (!hasSecurityConfig) {
            throw new GraphRunnerException("后端骨架缺少必需配置: SecurityConfig.java");
        }

        boolean hasCorsConfig = projectFiles.keySet().stream().anyMatch(p -> p.contains("CorsConfig.java"));
        if (!hasCorsConfig) {
            throw new GraphRunnerException("后端骨架缺少必需配置: CorsConfig.java (跨域工具)");
        }

        // 4. 包名校验：检查所有 Java 文件是否包含正确的 package 声明
        boolean hasWrongPackage = projectFiles.entrySet().stream()
                .filter(e -> e.getKey().startsWith("backend/src/main/java/") && e.getKey().endsWith(".java"))
                .anyMatch(e -> !e.getValue().contains("package " + basePackage));
        
        if (hasWrongPackage) {
            log.warn("检测到部分后端文件包名可能不匹配，期望前缀: package {}", basePackage);
            // 这里可以根据需求决定是直接抛异常还是仅警告
            // 为了保证项目可运行，建议对于 com.example 这种典型错误进行拦截
            boolean isExamplePackage = projectFiles.values().stream().anyMatch(v -> v.contains("package com.example"));
            if (isExamplePackage) {
                throw new GraphRunnerException("后端文件包名错误：检测到大量 com.example 包名，而非 " + basePackage);
            }
        }
    }

    /**
     * 基础输入校验（空值、长度、非法字符）
     */
    private void validateUserInput(String userInput) {
        // 1. 空值校验
        if (StringUtils.isBlank(userInput)) {
            throw new IllegalArgumentException("用户输入不能为空，请输入需要生成的系统需求");
        }
        // 2. 长度校验
        if (userInput.length() > MAX_INPUT_LENGTH) {
            throw new IllegalArgumentException("输入内容过长（最大支持" + MAX_INPUT_LENGTH + "个字符），请简化描述");
        }
        // 3. 非法字符校验（防止注入/路径遍历）
        if (ILLEGAL_CHAR_PATTERN.matcher(userInput).find()) {
            throw new IllegalArgumentException("输入包含非法字符（\\/:*?\"<>|），请删除后重试");
        }
    }

    /**
     * 提取用户输入中的系统名称（优化多正则匹配，提升容错性）
     */
    private String extractSystemName(String userInput) {
        // 遍历所有支持的正则，匹配到即返回
        for (Pattern pattern : SYSTEM_NAME_PATTERNS) {
            Matcher matcher = pattern.matcher(userInput);
            if (matcher.find()) {
                // 找到最后一个非空分组（适配不同正则的分组位置）
                String systemName = null;
                for (int i = matcher.groupCount(); i >= 1; i--) {
                    String group = matcher.group(i);
                    if (StringUtils.isNotBlank(group) && !group.matches("(为我|帮我|我要|编写|开发|创建|的|和|PRD|架构|需求|文档|开发)")) {
                        systemName = group.trim();
                        break;
                    }
                }
                // 校验系统名称有效性
                if (StringUtils.isBlank(systemName)) {
                    continue;
                }
                // 清理系统名称中的非法字符
                String cleanSystemName = ILLEGAL_CHAR_PATTERN.matcher(systemName).replaceAll("");
                if (StringUtils.isBlank(cleanSystemName)) {
                    throw new IllegalArgumentException("系统名称包含非法字符，无法识别");
                }
                return cleanSystemName;
            }
        }
        // 所有正则都匹配失败，返回友好提示
        throw new IllegalArgumentException(
                "未识别到有效系统名称！请按示例格式输入：\n" +
                        "1. 为我编写一个电商订单系统\n" +
                        "2. 帮我开发一个物流管理系统\n" +
                        "3. 生成库存管理系统的PRD和架构文档"
        );
    }

    // ========== 辅助方法：提升代码复用性 ==========
    /**
     * 生成短请求ID（便于日志追踪）
     */
    private String generateShortRequestId() {
        long timestampPart = System.currentTimeMillis() % 1000000L;
        int randomPart = (int) (Math.random() * 1000);
        return String.valueOf(timestampPart) + randomPart;
    }


    /**
     * 脱敏用户输入（日志中隐藏超长/敏感内容）
     */
    private String maskSensitiveInput(String input) {
        if (StringUtils.isBlank(input)) {
            return "";
        }
        return input.length() > 100 ? input.substring(0, 100) + "..." : input;
    }


}

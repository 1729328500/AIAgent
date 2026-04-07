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

                // 【关键改进】先处理缺失文件，再处理逻辑错误
                if (!report.getMissingFiles().isEmpty()) {
                    if (progressListener != null) {
                        progressListener.accept(String.format("发现 %d 个缺失文件，正在生成...", report.getMissingFiles().size()));
                    }
                    Map<String, String> missingFiles = generateMissingFiles(systemName, prdResult.getDocumentContent(),
                            archResult.getDocumentContent(), projectFiles, report.getMissingFiles(), attempt + 1);
                    projectFiles.putAll(missingFiles);
                    log.info("缺失文件生成完成 | 轮次: {} | 新增文件数: {}", attempt + 1, missingFiles.size());
                }

                // 再处理逻辑错误
                if (!report.getIssues().isEmpty()) {
                    if (progressListener != null) {
                        progressListener.accept(String.format("发现 %d 个逻辑问题，正在修复...", report.getIssues().size()));
                    }
                    Map<String, String> fixedFiles = fixLogicIssues(systemName, prdResult.getDocumentContent(),
                            archResult.getDocumentContent(), projectFiles, report.getIssues(), attempt + 1);
                    projectFiles.putAll(fixedFiles);
                    log.info("逻辑问题修复完成 | 轮次: {} | 修复文件数: {}", attempt + 1, fixedFiles.size());
                }

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
     * 专门处理缺失文件的生成
     */
    private Map<String, String> generateMissingFiles(String systemName, String prdContent, String archContent,
                                                     Map<String, String> currentFiles, List<String> missingFiles,
                                                     int attempt) throws GraphRunnerException {
        String basePackage = "com." + systemName.toLowerCase().replaceAll("[^a-z0-9]", "");

        StringBuilder prompt = new StringBuilder();
        prompt.append(String.format("你是一个资深全栈工程师。系统「%s」缺少以下文件，请生成它们 (第 %d 次尝试)。\n\n", systemName, attempt));

        prompt.append("### 项目信息:\n");
        prompt.append("- **项目包名**: ").append(basePackage).append("\n");
        prompt.append("- **已存在的文件数**: ").append(currentFiles.size()).append("\n\n");

        prompt.append("### 需要生成的缺失文件:\n");
        missingFiles.forEach(path -> prompt.append("- ").append(path).append("\n"));

        // 【关键】提供相关文件的完整内容作为参考
        prompt.append("\n### 相关文件内容（用于理解项目结构和依赖关系）:\n");

        // 智能提取相关文件：根据缺失文件路径找到可能的依赖
        java.util.Set<String> relatedFiles = new java.util.HashSet<>();
        for (String missingPath : missingFiles) {
            // 如果缺失的是 Service，提供对应的 Controller 和 Entity
            if (missingPath.contains("Service.java")) {
                String entityName = missingPath.replaceAll(".*/([A-Z]\\w+)Service\\.java", "$1");
                currentFiles.keySet().stream()
                    .filter(p -> p.contains(entityName + "Controller") || p.contains(entityName + ".java") || p.contains(entityName + "DTO"))
                    .forEach(relatedFiles::add);
            }
            // 如果缺失的是前端 API，提供对应的后端 Controller
            else if (missingPath.contains("api/") && missingPath.endsWith(".js")) {
                String apiName = missingPath.replaceAll(".*/([a-z]+)\\.js", "$1");
                currentFiles.keySet().stream()
                    .filter(p -> p.toLowerCase().contains(apiName.toLowerCase()) && p.contains("Controller"))
                    .forEach(relatedFiles::add);
            }
            // 如果缺失的是前端 View，提供路由和 API 文件
            else if (missingPath.contains("views/") && missingPath.endsWith(".vue")) {
                currentFiles.keySet().stream()
                    .filter(p -> p.contains("router/index") || p.contains("api/"))
                    .forEach(relatedFiles::add);
            }
        }

        // 输出相关文件的完整内容
        relatedFiles.forEach(path -> {
            if (currentFiles.containsKey(path)) {
                prompt.append("\n--- 文件: ").append(path).append(" ---\n");
                prompt.append(currentFiles.get(path)).append("\n");
            }
        });

        prompt.append("\n### PRD 文档（节选）:\n");
        prompt.append(prdContent.substring(0, Math.min(prdContent.length(), 2000))).append("\n\n");

        prompt.append("### 架构文档（节选）:\n");
        prompt.append(archContent.substring(0, Math.min(archContent.length(), 2000))).append("\n\n");

        prompt.append("### 生成要求:\n");
        prompt.append("1. **严格按照已有文件的风格和包名生成**，确保包名为 ").append(basePackage).append("\n");
        prompt.append("2. **前后端接口必须对齐**：API 路径、参数名、返回格式要与已有 Controller 一致\n");
        prompt.append("3. **依赖关系完整**：Service 要注入 Repository，Controller 要注入 Service\n");
        prompt.append("4. **输出格式**：每个文件必须严格按以下格式输出，不要有任何额外文字：\n");
        prompt.append("   [FILE_START] 完整路径（必须以 backend/ 或 frontend/ 开头）\n");
        prompt.append("   完整的文件内容...\n");
        prompt.append("   [FILE_END]\n");
        prompt.append("5. **禁止输出**：不要输出任何解释、注释、Markdown 代码块标签\n");

        Map<String, String> variables = new java.util.LinkedHashMap<>();
        variables.put("project.name", systemName);
        variables.put("missing.count", String.valueOf(missingFiles.size()));

        String raw = llmClient.call(systemName, prompt.toString(), variables);
        Map<String, String> generatedFiles = fileBlockParser.parse(raw);

        log.info("缺失文件生成完成 | 轮次: {} | 请求生成: {} | 实际生成: {}",
                attempt, missingFiles.size(), generatedFiles.size());

        return generatedFiles;
    }

    /**
     * 专门处理逻辑错误的修复
     */
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

        // 提取所有涉及文件的完整内容
        prompt.append("### 涉及文件的完整内容:\n");
        java.util.Set<String> involvedFiles = new java.util.HashSet<>();
        issues.forEach(issue -> {
            if (StringUtils.isNotBlank(issue.getFilePath())) {
                involvedFiles.add(issue.getFilePath());
            }
        });

        involvedFiles.forEach(path -> {
            if (currentFiles.containsKey(path)) {
                prompt.append("\n--- 文件: ").append(path).append(" ---\n");
                prompt.append(currentFiles.get(path)).append("\n");
            }
        });

        prompt.append("\n### 修复要求:\n");
        prompt.append("1. **只修复有问题的文件**，不要修改其他正常文件\n");
        prompt.append("2. **保持包名和路径不变**，确保包名为 ").append(basePackage).append("\n");
        prompt.append("3. **修复后的代码必须完整**，不要省略任何部分\n");
        prompt.append("4. **输出格式**：\n");
        prompt.append("   [FILE_START] 文件路径\n");
        prompt.append("   修复后的完整文件内容\n");
        prompt.append("   [FILE_END]\n");
        prompt.append("5. **禁止输出任何解释文字或 Markdown 标签**\n");

        Map<String, String> variables = new java.util.LinkedHashMap<>();
        variables.put("project.name", systemName);
        variables.put("issue.count", String.valueOf(issues.size()));

        String raw = llmClient.call(systemName, prompt.toString(), variables);
        Map<String, String> fixedFiles = fileBlockParser.parse(raw);

        log.info("逻辑问题修复完成 | 轮次: {} | 问题数: {} | 修复文件数: {}",
                attempt, issues.size(), fixedFiles.size());

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

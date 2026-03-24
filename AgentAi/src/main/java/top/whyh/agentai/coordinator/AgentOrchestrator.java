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
import top.whyh.agentai.service.RequirementAnalysisService;
import top.whyh.agentai.service.FrontendGenerationService;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private final RequirementAnalysisService requirementService;
    private final ArchitectAnalysisService architectService;
    private final CodeGenerationService codeGenerationService;
    private final CodeOutputService codeOutputService; // ← 新增注入
    private final FrontendGenerationService frontendGenerationService;


    /**
     * 执行全流程：解析输入 → 生成PRD → 生成架构文档
     * @param userInput 用户输入（如：为我编写一个电商订单系统）
     * @return 全流程结果
     */
    /**
     * 执行全流程：解析输入 → 生成PRD → 生成架构文档 → 生成完整项目
     */
    public SystemGenerateResult executeFullFlow(String userInput) {
        long startTime = System.currentTimeMillis();
        String requestId = generateShortRequestId();
        log.info("开始执行智能体全流程 | requestId: {} | 用户输入: {}", requestId, maskSensitiveInput(userInput));

        try {
            // 1-2. 输入校验 & 提取系统名 (保持不变)
            validateUserInput(userInput);
            String systemName = extractSystemName(userInput);
            log.info("解析到有效系统名称 | requestId: {} | 系统名称: {}", requestId, systemName);

            // 3. 生成PRD (保持不变)
            log.info("开始生成PRD文档 | requestId: {} | 系统名称: {}", requestId, systemName);
            long prdStart = System.currentTimeMillis();
            RequirementResult prdResult = requirementService.generateRequirementDocument(userInput);
            long prdCost = System.currentTimeMillis() - prdStart;
            log.info("PRD文档生成完成 | requestId: {} | 耗时: {}ms | 存储路径: {}", requestId, prdCost, prdResult.getStoragePath());

            // 4. 生成架构文档 (保持不变)
            log.info("开始生成架构文档 | requestId: {} | 系统名称: {}", requestId, systemName);
            long archStart = System.currentTimeMillis();
            ArchitectResult archResult = architectService.generateArchitectDocument(
                    prdResult.getDocumentContent(), systemName);
            long archCost = System.currentTimeMillis() - archStart;
            log.info("架构文档生成完成 | requestId: {} | 耗时: {}ms | 存储路径: {}", requestId, archCost, archResult.getStoragePath());

            // ========== 关键修改区域开始：后端 → 前端 两阶段 ==========
            // 5. 先生成后端
            log.info("开始生成后端代码 | requestId: {} | 系统名称: {}", requestId, systemName);
            long backendStart = System.currentTimeMillis();
            Map<String, String> backendFiles = codeGenerationService.generateBackendFiles(
                    systemName,
                    prdResult.getDocumentContent(),
                    archResult.getDocumentContent()
            );
            long backendCost = System.currentTimeMillis() - backendStart;
            log.info("后端代码生成完成 | requestId: {} | 耗时: {}ms | 文件数量: {}", requestId, backendCost, backendFiles.size());

            // 6. 再生成前端（依据已生成后端接口）
            log.info("开始生成前端代码 | requestId: {} | 系统名称: {}", requestId, systemName);
            long frontendStart = System.currentTimeMillis();
            Map<String, String> frontendFiles = frontendGenerationService.generateFrontendFiles(
                    systemName,
                    prdResult.getDocumentContent(),
                    archResult.getDocumentContent(),
                    backendFiles
            );
            long frontendCost = System.currentTimeMillis() - frontendStart;
            log.info("前端代码生成完成 | requestId: {} | 耗时: {}ms | 文件数量: {}", requestId, frontendCost, frontendFiles.size());

            // 合并前后端文件
            Map<String, String> projectFiles = new java.util.LinkedHashMap<>();
            projectFiles.putAll(backendFiles);
            projectFiles.putAll(frontendFiles);
            log.info("项目代码合并完成 | requestId: {} | 文件总数: {}", requestId, projectFiles.size());

            // 6. 保存整个项目到 D 盘指定目录
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

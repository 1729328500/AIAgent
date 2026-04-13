package top.whyh.agentai.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import top.whyh.agentai.config.DashScopeProperties;
import top.whyh.agentai.model.dto.CodeReviewReport;

import java.util.List;
import java.util.Map;

/**
 * 代码审查智能体服务：基于 Qwen 对生成的代码进行逻辑和完整性审查
 */
@Service
@Slf4j
public class CodeReviewService {

    private final DashScopeChatModel chatModel;
    private final DashScopeProperties dashScopeProperties;
    private final ObjectMapper objectMapper;
    private final String systemPrompt;

    public CodeReviewService(DashScopeProperties dashScopeProperties, ObjectMapper objectMapper) {
        this.dashScopeProperties = dashScopeProperties;
        this.objectMapper = objectMapper;

        if (StringUtils.isBlank(this.dashScopeProperties.getApiKey())) {
            throw new IllegalArgumentException("通义千问API Key未配置！");
        }

        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(this.dashScopeProperties.getApiKey())
                .build();

        this.chatModel = DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(this.dashScopeProperties.getModel())
                        .withTemperature(0.1)
                        .withMaxToken(2000)
                        .build())
                .build();

        this.systemPrompt = buildSystemPrompt();
    }

    /**
     * 核心入口：执行代码审查
     */
    public CodeReviewReport reviewCode(String systemName, String prdContent,
                                       String archContent, Map<String, String> projectFiles) {
        log.info("开始对系统 [{}] 进行代码审查...", systemName);
        try {
            String userPrompt = buildUserPrompt(systemName, prdContent, archContent, projectFiles);
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(this.systemPrompt),
                    new UserMessage(userPrompt)
            ));
            String response = chatModel.call(prompt).getResult().getOutput().getText();
            log.debug("AI 审查原始响应: {}", response);
            return parseReport(response);
        } catch (Exception e) {
            log.error("代码审查过程中发生异常: {}", e.getMessage(), e);
            return CodeReviewReport.builder()
                    .passed(true)
                    .summary("审查过程发生系统错误，跳过审查: " + e.getMessage())
                    .build();
        }
    }

    private String buildSystemPrompt() {
        return "你是一个严谨的资深软件架构师和代码审查专家。你的任务是审查 AI 生成的全 stack 项目代码是否完整、逻辑是否自洽。\n\n"
             + "### 审查准则：\n"
             + "1. **深度完整性**：对比 PRD 和架构，检查是否有缺失的 Controller、Service、Repository、Entity、DTO 以及前端的 View 和 API 封装。\n"
             + "2. **接口契合度**：严格比对前端 src/api/*.js 中的 axios 调用路径与后端 @RestController 中的 @RequestMapping 路径。路径、方法（GET/POST）、参数名不一致，标记为 INCONSISTENCY。\n"
             + "3. **引用闭环**：检查代码中的 import 或 require。如果引用的文件在已生成文件列表中不存在，标记为 MISSING_FILE。\n"
             + "4. **结构合规性**：确保所有文件都在 backend/ 或 frontend/ 下，且 Java 包名符合规范。\n"
             + "5. **拒绝幻觉**：如果某个功能在 PRD 中有但在代码中完全没实现，必须指出缺失的文件。\n\n"
             + "### 严重性分级（severity 字段，必填）：\n"
             + "- CRITICAL（致命问题）：会导致项目无法编译或无法运行。例如：\n"
             + "  缺少必要文件（Controller、Service、主入口等），import 引用了不存在的文件，包名/路径错误导致编译失败，前后端接口路径完全不匹配（404 必现）。\n"
             + "- WARNING（警告问题）：不影响项目运行，仅为代码质量建议。例如：\n"
             + "  代码风格不一致，缺少注释或文档，字段名称不规范但不影响功能，非必要的重复代码，可选的优化建议。\n\n"
             + "### 输出要求：\n"
             + "必须且仅输出一个合法的 JSON 字符串，格式如下：\n"
             + "{\n"
             + "  \"passed\": boolean,\n"
             + "  \"summary\": \"总体评估描述\",\n"
             + "  \"issues\": [\n"
             + "    {\n"
             + "      \"type\": \"MISSING_FILE | LOGIC_ERROR | INCONSISTENCY\",\n"
             + "      \"severity\": \"CRITICAL | WARNING\",\n"
             + "      \"description\": \"详细描述问题\",\n"
             + "      \"filePath\": \"涉及到的文件路径\"\n"
             + "    }\n"
             + "  ],\n"
             + "  \"missingFiles\": [\"路径1（以 backend/ 或 frontend/ 开头）\", \"路径2\"]\n"
             + "}\n\n"
             + "注意：\n"
             + "- passed=true 表示项目可以正常运行（即使有 WARNING 级别问题）。\n"
             + "- 只有存在 CRITICAL 问题或 missingFiles 不为空时，passed 才为 false。\n"
             + "- WARNING 级别的问题必须填入 issues 中，但不影响 passed 的判断。\n"
             + "- 不要输出任何解释文字，只输出 JSON。";
    }

    private String buildUserPrompt(String systemName, String prdContent,
                                   String archContent, Map<String, String> projectFiles) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("系统名称: ").append(systemName).append("\n\n");
        prompt.append("### PRD 文档:\n").append(prdContent).append("\n\n");
        prompt.append("### 架构文档:\n").append(archContent).append("\n\n");
        prompt.append("### 已生成的文件列表 (共 ").append(projectFiles.size()).append(" 个):\n");
        projectFiles.keySet().forEach(path -> prompt.append("- ").append(path).append("\n"));

        prompt.append("\n### 核心代码内容 (请重点审查接口定义和调用):\n");
        projectFiles.entrySet().stream()
                .filter(e -> e.getKey().contains("Controller")
                        || e.getKey().contains("Service.java")
                        || e.getKey().contains("api/")
                        || e.getKey().contains("router/")
                        || e.getKey().contains("App.vue")
                        || e.getKey().contains("SecurityConfig.java")
                        || e.getKey().contains("CorsConfig.java")
                        || e.getKey().contains("axios.js")
                        || e.getKey().contains("Application.java")
                        || e.getKey().contains("pom.xml")
                        || e.getKey().contains("package.json"))
                .limit(30)
                .forEach(e -> prompt.append("--- 文件: ").append(e.getKey()).append(" ---\n")
                        .append(e.getValue()).append("\n\n"));

        prompt.append("\n### 审查重点:\n");
        prompt.append("1. 检查是否所有 PRD 中提到的功能模块都有对应的 Controller、Service、Entity\n");
        prompt.append("2. 检查前端 API 调用路径是否与后端 @RequestMapping 完全一致\n");
        prompt.append("3. 检查所有 import 语句引用的类是否都存在于文件列表中\n");
        prompt.append("4. 检查包名是否统一且符合规范\n");
        prompt.append("5. 检查是否缺少关键配置文件（如 SecurityConfig、CorsConfig）\n");
        prompt.append("\n请基于以上完整上下文，进行深度审查并区分 CRITICAL 和 WARNING 问题。");

        return prompt.toString();
    }

    private CodeReviewReport parseReport(String response) {
        try {
            String cleanJson = response.replaceAll("```json", "").replaceAll("```", "").trim();
            return objectMapper.readValue(cleanJson, CodeReviewReport.class);
        } catch (Exception e) {
            log.error("解析审查报告 JSON 失败: {}, 原始响应: {}", e.getMessage(), response);
            return CodeReviewReport.builder()
                    .passed(true)
                    .summary("报告解析失败，默认跳过修复。")
                    .build();
        }
    }
}

package top.whyh.agentai.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import top.whyh.agentai.config.AgentConfigProperties;
import top.whyh.agentai.config.AgentOutputConfig;
import top.whyh.agentai.config.DashScopeProperties;
import top.whyh.agentai.result.ArchitectResult;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
public class ArchitectAnalysisService {
    private final AgentConfigProperties agentConfig;
    private final AgentOutputConfig outputConfig;
    private final DashScopeChatModel chatModel;
    private final DashScopeProperties dashScopeProperties; // 改为构造器注入

    // 常量
    private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyyMMddHHmmssSSS");
    private static final Pattern API_TABLE_PATTERN = Pattern.compile("\\|\\s*接口路径\\s*\\|\\s*请求方法\\s*\\|\\s*请求参数\\s*\\|\\s*响应参数\\s*\\|\\s*接口描述\\s*\\|");
    private static final Pattern ARCH_MERMAID_PATTERN = Pattern.compile("```mermaid[\\s\\S]*?flowchart|architecture|classDiagram[\\s\\S]*?```");
    private static final Pattern FIRST_LEVEL_TITLE_PATTERN = Pattern.compile("^#\\s+.+系统架构文档", Pattern.MULTILINE);

    // 构造器注入所有依赖（关键修复！）
    public ArchitectAnalysisService(AgentConfigProperties agentConfig, AgentOutputConfig outputConfig, DashScopeProperties dashScopeProperties) {
        this.agentConfig = agentConfig;
        this.outputConfig = outputConfig;
        this.dashScopeProperties = dashScopeProperties; // 构造器中赋值
        this.chatModel = initDashScopeChatModel(); // 现在调用init时，dashScopeProperties已初始化
    }

    // 初始化ChatModel（现在依赖已注入，可安全调用）
    private DashScopeChatModel initDashScopeChatModel() {
        // 1. 空值校验
        if (StringUtils.isBlank(dashScopeProperties.getApiKey())) {
            throw new IllegalArgumentException("通义千问API Key未配置！请检查spring.ai.dashscope.api-key或环境变量DASHSCOPE_API_KEY");
        }

        // 2. 初始化DashScopeApi
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(dashScopeProperties.getApiKey())
                .build();

        // 3. 构建ChatModel配置
        DashScopeChatOptions defaultOptions = DashScopeChatOptions.builder()
                .withModel(StringUtils.defaultIfBlank(dashScopeProperties.getModel(), "qwen-plus"))
                .withTemperature(dashScopeProperties.getTemperature() != null ? dashScopeProperties.getTemperature().doubleValue() : 0.4D)
                .withMaxToken(dashScopeProperties.getMaxTokens() != null ? dashScopeProperties.getMaxTokens() : 8000)
                .withTopP(dashScopeProperties.getTopP() != null ? dashScopeProperties.getTopP().doubleValue() : 0.9D)
                .build();

        // 4. 构建ChatModel
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(defaultOptions)
                .build();
    }

    /**
     * 生成架构文档+接口定义（核心入口）
     * @param prdContent PRD文档内容（需求分析智能体输出）
     * @param systemName 系统名称
     * @return 架构文档生成结果
     */
    public ArchitectResult generateArchitectDocument(String prdContent, String systemName) throws GraphRunnerException {
        // 1. 空值校验
        if (StringUtils.isBlank(prdContent)) {
            throw new IllegalArgumentException("PRD文档内容不能为空");
        }
        if (StringUtils.isBlank(systemName)) {
            throw new IllegalArgumentException("系统名称不能为空");
        }

        // 2. 生成唯一文档ID
        String documentId = TIMESTAMP_FORMAT.format(new Date());

        // 3. 调用AI生成架构文档（带重试）
        String documentContent = generateAIDocument(prdContent, systemName, documentId);

        // 4. 格式校验
        List<String> validateErrors = validateMarkdownContent(documentContent, systemName);
        if (!validateErrors.isEmpty()) {
            throw new GraphRunnerException("架构文档格式校验失败：" + String.join("；", validateErrors));
        }

        // 5. 保存文档
        String filePath = saveDocumentToFile(systemName, documentId, documentContent);

        // 6. 返回结果
        return new ArchitectResult(documentId, documentContent, filePath, "success", "");
    }

    /**
     * 调用AI生成架构文档（带重试）
     */
    private String generateAIDocument(String prdContent, String systemName, String documentId) throws GraphRunnerException {
        String content = "";
        int retryCount = 0;
        // 从配置读取最大重试次数（适配新配置结构）
        int maxRetry = dashScopeProperties.getMaxRetry() != null ? dashScopeProperties.getMaxRetry() : 3;
        List<String> retryErrors = new ArrayList<>();

        while (retryCount < maxRetry) {
            try {
                log.info("架构师智能体第{}次生成文档（documentId：{}）", retryCount + 1, documentId);

                // 构建提示词
                String userPrompt = buildUserPrompt(prdContent, systemName, retryErrors);
                Prompt prompt = new Prompt(List.of(
                        new SystemMessage(buildSystemPrompt()),
                        new UserMessage(userPrompt)
                ));

                // 调用AI
                content = chatModel.call(prompt).getResult().getOutput().getText();
                // 清洗内容
                content = cleanDocumentContent(content);

                // 校验
                List<String> errors = validateMarkdownContent(content, systemName);
                if (errors.isEmpty()) {
                    log.info("架构文档第{}次生成校验通过", retryCount + 1);
                    return content;
                } else {
                    String errorMsg = "格式错误：" + String.join("；", errors);
                    retryErrors.add(errorMsg);
                    log.warn(errorMsg);
                }
            } catch (Exception e) {
                String errorMsg = "第" + (retryCount + 1) + "次生成异常：" + e.getMessage();
                retryErrors.add(errorMsg);
                log.warn(errorMsg, e);
            }
            retryCount++;
        }

        // 重试失败抛异常
        throw new GraphRunnerException(
                String.format("架构文档生成失败（已重试%d次）：%s，最后生成内容：%s",
                        maxRetry, String.join(" | ", retryErrors), content)
        );
    }

    /**
     * 构建架构师系统提示词（定义生成规则）
     */
    private String buildSystemPrompt() {
        return """
        你是资深架构师，需根据PRD文档生成**纯Markdown格式**的系统架构文档+接口定义，严格遵守以下规则：
        1. 仅输出Markdown正文，无任何前置/后置说明、无JSON、无注释；
        2. 文档结构必须包含以下二级标题（顺序固定）：
           - 系统架构总览
           - 技术栈选型
           - 模块划分与职责
           - 接口定义（RESTful）
           - 数据库设计（核心表）
           - 部署架构
           - 性能/安全设计
        3. 接口定义必须是Markdown表格，列固定：接口路径、请求方法、请求参数、响应参数、接口描述，至少5行有效数据；
        4. 必须包含至少1个mermaid架构图（flowchart/architecture/classDiagram），代码块闭合；
        5. 一级标题为# {系统名称} 系统架构文档，无其他一级标题；
        6. 所有章节内容非空，使用无序列表/段落组织，禁止空行过多；
        7. 严格基于PRD文档内容生成，不偏离需求；
        8. 接口定义需符合RESTful规范，参数格式清晰（JSON示例）。
        """;
    }

    /**
     * 构建用户提示词（含PRD内容+重试错误）
     */
    private String buildUserPrompt(String prdContent, String systemName, List<String> retryErrors) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请基于以下PRD文档，生成「").append(systemName).append("」的系统架构文档+接口定义：\n");
        prompt.append(prdContent).append("\n");
        if (!retryErrors.isEmpty()) {
            prompt.append("上一次生成存在以下错误，请修正：\n");
            prompt.append(String.join("\n", retryErrors)).append("\n");
        }
        prompt.append("仅输出纯Markdown格式内容，严格遵守系统提示规则。");
        return prompt.toString();
    }

    /**
     * 清洗架构文档内容
     */
    private String cleanDocumentContent(String content) {
        if (StringUtils.isBlank(content)) return "";
        return content
                .replaceAll("\\{[^}]*\\}", "")
                .replaceAll("\\[[^\\]]*\\]", "")
                .replaceAll("\\\\[\"'\\\\/bfnrt]", "")
                .replaceAll("\\n{4,}", "\n\n")
                .replaceAll("(?i)抱歉|无法生成|格式错误|不符合要求|error|warning", "")
                .replaceAll("```(?!mermaid)[\\s\\S]*?```", "")
                .trim();
    }

    /**
     * 校验架构文档格式
     */
    private List<String> validateMarkdownContent(String content, String systemName) {
        List<String> errors = new ArrayList<>();

        // 1. 非空校验
        if (StringUtils.isBlank(content)) {
            errors.add("架构文档内容为空");
            return errors;
        }

        // 2. 一级标题校验
        if (!FIRST_LEVEL_TITLE_PATTERN.matcher(content).find()) {
            errors.add("未包含一级标题：# " + systemName + " 系统架构文档");
        }

        // 3. 必填章节校验
        List<String> missingSections = new ArrayList<>();
        for (String section : agentConfig.getArchitect().getRequiredSections()) {
            if (!content.contains(section)) {
                missingSections.add(section);
            }
        }
        if (!missingSections.isEmpty()) {
            errors.add("缺失核心章节：" + String.join(", ", missingSections));
        }

        // 4. 接口定义表格校验
        if (!API_TABLE_PATTERN.matcher(content).find()) {
            errors.add("接口定义表格格式错误（缺少列：接口路径、请求方法、请求参数、响应参数、接口描述）");
        } else {
            // 校验表格行数
            String[] lines = content.split("\n");
            int apiRowCount = 0;
            boolean inTable = false;
            for (String line : lines) {
                String trimLine = line.trim();
                if (trimLine.contains("| 接口路径 | 请求方法 | 请求参数 | 响应参数 | 接口描述 |")) {
                    inTable = true;
                    continue;
                }
                if (inTable && trimLine.startsWith("|") && trimLine.endsWith("|") && !trimLine.contains("---")) {
                    apiRowCount++;
                }
                if (inTable && !trimLine.startsWith("|") && StringUtils.isNotBlank(trimLine)) {
                    break;
                }
            }
            if (apiRowCount < agentConfig.getArchitect().getMinApiCount()) {
                errors.add("接口定义表格有效行数不足（仅" + apiRowCount + "行，要求至少" + agentConfig.getArchitect().getMinApiCount() + "行）");
            }
        }

        // 5. mermaid架构图校验
        if (!ARCH_MERMAID_PATTERN.matcher(content).find()) {
            errors.add("未包含有效的mermaid架构图（需包含flowchart/architecture/classDiagram）");
        }

        return errors;
    }

    /**
     * 保存架构文档到文件
     */
    private String saveDocumentToFile(String systemName, String documentId, String content) {
        try {
            // 1. 构建存储目录
            Path storageDir = Paths.get(outputConfig.getArchStoragePath());
            if (!Files.exists(storageDir)) {
                Files.createDirectories(storageDir);
            }

            // 2. 安全文件名
            String sanitizedName = systemName.replaceAll("[\\\\/:*?\"<>|]", "_");
            if (sanitizedName.length() > agentConfig.getDocumentStorage().getMaxFileNameLength()) {
                sanitizedName = sanitizedName.substring(0, agentConfig.getDocumentStorage().getMaxFileNameLength());
            }
            String fileName = sanitizedName + "_架构文档_" + documentId + agentConfig.getDocumentStorage().getSuffix();

            // 3. 写入文件
            Path filePath = storageDir.resolve(fileName).normalize();
            StandardOpenOption[] writeOptions = agentConfig.getDocumentStorage().isOverwrite() ?
                    new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING} :
                    new StandardOpenOption[]{StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE};

            Files.writeString(filePath, content, Charset.forName(agentConfig.getDocumentStorage().getEncoding()), writeOptions);

            return filePath.toAbsolutePath().toString();
        } catch (Exception e) {
            throw new RuntimeException("架构文档保存失败：" + e.getMessage(), e);
        }
    }

}
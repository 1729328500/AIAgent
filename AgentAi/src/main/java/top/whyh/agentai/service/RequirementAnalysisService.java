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
import top.whyh.agentai.result.RequirementResult;

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
public class RequirementAnalysisService {

    private final DashScopeChatModel chatModel;
    private final AgentConfigProperties agentConfig;
    private final AgentOutputConfig outputConfig;
    private final DashScopeProperties dashScopeProperties; // 改为构造器注入
    private final String systemPrompt;


    // 正则常量
    private static final Pattern ILLEGAL_FILE_NAME_PATTERN = Pattern.compile("[\\\\/:*?\"<>|]");
    private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyyMMddHHmmssSSS");
    private static final Pattern DATA_DICTIONARY_TABLE_PATTERN = Pattern.compile("\\|\\s*字段名\\s*\\|\\s*数据类型\\s*\\|\\s*描述\\s*\\|\\s*约束\\s*\\|");
    private static final Pattern MERMAID_CODE_BLOCK_PATTERN = Pattern.compile("```mermaid[\\s\\S]*?```");
    private static final Pattern FIRST_LEVEL_TITLE_PATTERN = Pattern.compile("^#\\s+.+需求分析文档", Pattern.MULTILINE);

    // 构造器注入所有依赖（关键修复！）
    public RequirementAnalysisService(AgentConfigProperties agentConfig, AgentOutputConfig outputConfig, DashScopeProperties dashScopeProperties) {
        this.agentConfig = agentConfig;
        this.outputConfig = outputConfig;
        this.dashScopeProperties = dashScopeProperties; // 构造器中赋值

        // 1. 空值校验（提前拦截配置问题）
        if (StringUtils.isBlank(this.dashScopeProperties.getApiKey())) {
            throw new IllegalArgumentException("通义千问API Key未配置！请检查spring.ai.dashscope.api-key配置");
        }

        // 2. 初始化DashScopeApi
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(this.dashScopeProperties.getApiKey())
                .build();

        // 3. 初始化ChatModel
        this.chatModel = DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(this.dashScopeProperties.getModel())
                        .withTemperature(this.dashScopeProperties.getTemperature().doubleValue())
                        .withMaxToken(this.dashScopeProperties.getMaxTokens())
                        .withTopP(this.dashScopeProperties.getTopP().doubleValue())
                        .build())
                .build();

        // 4. 构建系统提示词
        this.systemPrompt = buildSystemPrompt();
    }

    /**
     * 核心入口：生成需求分析文档（PRD）
     * @param userRequirement 用户输入的需求（如：为我编写一个电商订单系统）
     * @return 需求文档生成结果
     * @throws GraphRunnerException 生成失败异常
     */
    public RequirementResult generateRequirementDocument(String userRequirement) throws GraphRunnerException {
        // 1. 空值校验
        if (StringUtils.isBlank(userRequirement)) {
            throw new IllegalArgumentException("用户需求不能为空");
        }

        // 2. 生成唯一文档ID
        String documentId = TIMESTAMP_FORMAT.format(new Date());
        log.info("开始生成需求分析文档（documentId：{}）", documentId);

        // 3. 调用AI生成文档（带重试）
        String documentContent = generateAIDocument(userRequirement, documentId);

        // 4. 格式校验
        List<String> validateErrors = validateMarkdownContent(documentContent);
        if (!validateErrors.isEmpty()) {
            throw new GraphRunnerException("需求文档格式校验失败：" + String.join("；", validateErrors));
        }

        // 5. 保存文档到文件
        String storagePath = saveDocumentToFile(userRequirement, documentId, documentContent);
        log.info("需求分析文档生成完成，存储路径：{}", storagePath);

        // 6. 返回结果
        return new RequirementResult(documentId, documentContent, storagePath, "success", "");
    }

    /**
     * 调用AI生成需求文档（带重试机制）
     */
    private String generateAIDocument(String userRequirement, String documentId) throws GraphRunnerException {
        String content = "";
        int retryCount = 0;
        int maxRetry = dashScopeProperties.getMaxRetry() != null ? dashScopeProperties.getMaxRetry() : 3;
        List<String> retryErrors = new ArrayList<>();

        while (retryCount < maxRetry) {
            try {
                log.info("需求分析智能体第{}次生成文档（documentId：{}）", retryCount + 1, documentId);

                // 构建Prompt
                Prompt prompt = new Prompt(List.of(
                        new SystemMessage(this.systemPrompt),
                        new UserMessage(buildUserPrompt(userRequirement, retryErrors))
                ));

                // 调用AI生成内容
                content = chatModel.call(prompt).getResult().getOutput().getText();
                // 清洗内容
                content = cleanDocumentContent(content);

                // 校验生成结果
                List<String> errors = validateMarkdownContent(content);
                if (errors.isEmpty()) {
                    log.info("需求文档第{}次生成校验通过", retryCount + 1);
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
                String.format("需求文档生成失败（已重试%d次）：%s，最后生成内容：%s",
                        maxRetry, String.join(" | ", retryErrors), content)
        );
    }

    /**
     * 构建用户提示词（含重试错误提示）
     */
    private String buildUserPrompt(String userRequirement, List<String> retryErrors) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请根据以下用户需求，生成完整的需求分析文档：\n");
        prompt.append(userRequirement).append("\n");
        if (!retryErrors.isEmpty()) {
            prompt.append("上一次生成存在以下错误，请完全修正后重新输出完整文档：\n");
            prompt.append(String.join("\n", retryErrors)).append("\n");
        }
        prompt.append("严格遵守系统提示的所有规则，仅输出纯Markdown格式内容。");
        return prompt.toString();
    }

    /**
     * 清洗需求文档内容（移除无效字符/格式）
     */
    private String cleanDocumentContent(String content) {
        if (StringUtils.isBlank(content)) return "";
        return content
                .replaceAll("\\{[^}]*\\}", "")
                .replaceAll("\\\\[\"'\\\\/bfnrt]", "")
                .replaceAll("\\n{4,}", "\n\n")
                .replaceAll("(?i)抱歉|无法生成|格式错误|不符合要求|error|warning", "")
                .trim();
    }

    /**
     * 构建系统提示词（使用配置类中的必填章节）
     */
    private String buildSystemPrompt() {
        return String.format("""
        你的唯一职责是生成**纯Markdown格式**的需求分析文档，严格遵守以下铁律，违反任何一条都算失败：
        1. 输出内容**仅允许**包含Markdown正文，无任何前置/后置说明、无JSON、无注释、无错误提示、无格式解释；
        2. 文档结构必须严格按以下顺序包含所有二级标题（##），缺一不可：
           - %s
        3. 数据字典必须是Markdown表格，列固定为：字段名、数据类型、描述、约束，且至少包含%d行有效数据；
        4. 必须包含至少1个完整的```mermaid代码块（类图/时序图），代码块必须闭合；
        5. 文档标题为# {系统名称} 需求分析文档（一级标题），无其他一级标题；
        6. 所有章节内容不能为空，使用无序列表（-）或段落组织内容，禁止空行过多；
        7. 无论用户输入什么，都必须输出纯Markdown，拒绝任何其他格式的请求；
        8. 若之前生成的内容不符合要求，需完全修正所有问题后重新输出完整MD文档，不保留任何错误内容。
        """, String.join("\n           - ", agentConfig.getRequirement().getRequiredSections()),
                agentConfig.getRequirement().getMinDataDictRows());
    }

    /**
     * 保存文档到文件（使用通用配置）
     */
    private String saveDocumentToFile(String userRequirement, String timeStamp, String content) {
        try {
            // 1. 创建存储目录（使用通用配置路径）
            Path storageDir = Paths.get(outputConfig.getPrdStoragePath());
            if (!Files.exists(storageDir)) {
                Files.createDirectories(storageDir);
            }

            // 2. 构建安全的文件名（使用通用配置的最大长度）
            String sanitizedFileName = sanitizeFileName(userRequirement) + "_" + timeStamp;
            String fileName = sanitizedFileName + (agentConfig.getDocumentStorage().getSuffix().startsWith(".") ?
                    agentConfig.getDocumentStorage().getSuffix() : "." + agentConfig.getDocumentStorage().getSuffix());

            // 3. 构建完整路径
            Path filePath = storageDir.resolve(fileName).normalize();
            if (!filePath.startsWith(storageDir)) {
                throw new RuntimeException("非法文件路径：" + filePath);
            }

            // 4. 写入选项（使用通用配置的overwrite）
            StandardOpenOption[] writeOptions = agentConfig.getDocumentStorage().isOverwrite() ?
                    new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING} :
                    new StandardOpenOption[]{StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE};

            // 5. 写入文件（使用通用配置的编码）
            Files.writeString(
                    filePath,
                    content,
                    Charset.forName(agentConfig.getDocumentStorage().getEncoding()),
                    writeOptions
            );

            return filePath.toAbsolutePath().toString();
        } catch (Exception e) {
            throw new RuntimeException("文档存储失败：" + e.getMessage(), e);
        }
    }

    /**
     * 清洗文件名（使用通用配置的最大长度）
     */
    private String sanitizeFileName(String userInput) {
        if (StringUtils.isBlank(userInput)) {
            return "未命名需求文档";
        }
        String sanitized = ILLEGAL_FILE_NAME_PATTERN.matcher(userInput).replaceAll("_");
        // 使用通用配置的最大文件名长度
        if (sanitized.length() > agentConfig.getDocumentStorage().getMaxFileNameLength()) {
            sanitized = sanitized.substring(0, agentConfig.getDocumentStorage().getMaxFileNameLength());
        }
        sanitized = sanitized.trim().replaceAll("^_+|_+$", "");
        return StringUtils.isBlank(sanitized) ? "未命名需求文档" : sanitized;
    }

    /**
     * 校验Markdown内容（使用配置类的必填章节和最小行数）
     */
    private List<String> validateMarkdownContent(String content) {
        List<String> errors = new ArrayList<>();

        // 1. 非空校验
        if (StringUtils.isBlank(content)) {
            errors.add("文档内容为空");
            return errors;
        }

        // 2. 一级标题校验
        if (!FIRST_LEVEL_TITLE_PATTERN.matcher(content).find()) {
            errors.add("未包含符合要求的一级标题（格式：# {系统名称} 需求分析文档）");
        }

        // 3. 必填章节校验（使用配置类的章节列表）
        List<String> missingSections = new ArrayList<>();
        for (String section : agentConfig.getRequirement().getRequiredSections()) {
            if (!content.contains(section)) {
                missingSections.add(section);
            }
        }
        if (!missingSections.isEmpty()) {
            errors.add("缺失核心章节：" + String.join(", ", missingSections));
        }

        // 4. 数据字典校验（使用配置类的最小行数）
        boolean hasValidTable = DATA_DICTIONARY_TABLE_PATTERN.matcher(content).find();
        if (!hasValidTable) {
            errors.add("数据字典表格格式不符合要求（缺少指定列：字段名、数据类型、描述、约束）");
        } else {
            String[] tableLines = content.split("\n");
            int tableRowCount = 0;
            boolean inTable = false;
            for (String line : tableLines) {
                String trimLine = line.trim();
                if (trimLine.contains("| 字段名 | 数据类型 | 描述 | 约束 |")) {
                    inTable = true;
                    continue;
                }
                if (inTable && trimLine.startsWith("|") && trimLine.endsWith("|")) {
                    if (!trimLine.contains("---") && StringUtils.isNotBlank(trimLine.replace("|", "").trim())) {
                        tableRowCount++;
                    }
                }
                if (inTable && !trimLine.startsWith("|") && StringUtils.isNotBlank(trimLine)) {
                    break;
                }
            }
            if (tableRowCount < agentConfig.getRequirement().getMinDataDictRows()) {
                errors.add(String.format("数据字典表格有效行数不足（仅%d行，要求至少%d行）",
                        tableRowCount, agentConfig.getRequirement().getMinDataDictRows()));
            }
        }

        // 5. mermaid校验（使用配置类的开关）
        if (agentConfig.getRequirement().isCheckMermaid() && !MERMAID_CODE_BLOCK_PATTERN.matcher(content).find()) {
            errors.add("未包含有效的mermaid代码块（需包含```mermaid和```闭合标签）");
        }

        // 6. 章节内容非空校验
        for (String section : agentConfig.getRequirement().getRequiredSections()) {
            if (content.contains(section)) {
                int sectionStart = content.indexOf(section) + section.length();
                int nextSectionStart = content.length();
                for (String nextSection : agentConfig.getRequirement().getRequiredSections()) {
                    int idx = content.indexOf(nextSection, sectionStart);
                    if (idx > sectionStart && idx < nextSectionStart) {
                        nextSectionStart = idx;
                    }
                }
                String sectionContent = content.substring(sectionStart, nextSectionStart).trim();
                if (StringUtils.isBlank(sectionContent)) {
                    errors.add(String.format("章节「%s」内容为空", section));
                }
            }
        }

        return errors;
    }
}
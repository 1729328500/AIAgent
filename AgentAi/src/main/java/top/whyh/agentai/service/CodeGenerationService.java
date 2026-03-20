// top/whyh/agentai/service/CodeGenerationService.java

package top.whyh.agentai.service;

import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import top.whyh.agentai.config.CoderAgentConfig;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeGenerationService {

    private final CoderAgentConfig coderConfig;
    private final RestTemplate restTemplate = new RestTemplate();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


    // ====== 新增：用于解析 [FILE_START] / [FILE_END] 的正则 ======
    private static final Pattern FILE_BLOCK_PATTERN = Pattern.compile(
            "\\[FILE_START\\]\\s*(.+?)\\s*\\n(.*?)\\s*\\[FILE_END\\]",
            Pattern.DOTALL | Pattern.MULTILINE
    );

    @Retryable(
            value = {GraphRunnerException.class},
            maxAttemptsExpression = "#{@coderConfig.maxRetry + 1}",
            backoff = @Backoff(delay = 2000)
    )
    public String generateCode(String systemName, String prdContent, String archContent) throws GraphRunnerException {
        if (!coderConfig.isEnabled()) {
            return "// 编程智能体未启用，跳过代码生成";
        }

        if (StringUtils.isAnyBlank(coderConfig.getBaseUrl(), coderConfig.getWorkspaceSlug(), coderConfig.getApiKey())) {
            throw new IllegalArgumentException("编码智能体需要配置 agent.ai.coder.base-url, workspace-slug 和 api-key");
        }

        String userPrompt = buildUserPromptForAnythingLLM(systemName, prdContent, archContent);

        String url = String.format(
                "%s/api/v1/workspace/%s/chat",
                coderConfig.getBaseUrl().replaceAll("/+$", ""),
                coderConfig.getWorkspaceSlug()
        );

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("message", userPrompt);
        requestBody.put("mode", "chat");
        requestBody.put("sessionId", "agent-session-" + System.currentTimeMillis());

        // ✅ 修正点 1：添加 Accept 头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON)); // ←←← 新增
        headers.set("Authorization", "Bearer " + coderConfig.getApiKey().trim());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            log.info("调用 AnythingLLM 编码智能体 | URL: {} | System: {}", url, systemName);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = OBJECT_MAPPER.readTree(response.getBody());

                // ✅ 修正点 2：字段名是 textResponse
                JsonNode textNode = root.path("textResponse");

                if (!textNode.isMissingNode() && !textNode.asText().isEmpty()) {
                    String code = textNode.asText();
                    log.info("✅ 代码生成成功，长度：{} 字符", code.length());
                    return code;
                } else {
                    throw new GraphRunnerException("AnythingLLM 响应中无有效代码输出: " + response.getBody());
                }
            } else {
                throw new GraphRunnerException(
                        String.format("调用 AnythingLLM 失败 [%s]: %s", response.getStatusCode(), response.getBody())
                );
            }
        } catch (RestClientException e) {
            log.error("网络异常", e);
            throw new GraphRunnerException("调用 AnythingLLM 失败：" + e.getMessage(), e);
        } catch (Exception e) {
            log.error("解析响应失败", e);
            throw new GraphRunnerException("解析 AnythingLLM 响应失败：" + e.getMessage(), e);
        }
    }

    // ========== 新增核心方法：生成并解析项目文件 ==========
    /**
     * 调用 AI 生成代码，并解析为 Map<相对文件路径, 文件内容>
     */
    public Map<String, String> generateProjectFiles(String systemName, String prdContent, String archContent) throws GraphRunnerException {
        // 1. 先获取原始的、包含所有文件块的完整响应
        String rawResponse = generateCode(systemName, prdContent, archContent);

        // 2. 如果智能体被禁用，直接返回空Map
        if (rawResponse.contains("// 编程智能体未启用")) {
            return new HashMap<>();
        }

        // 3. 开始解析
        Map<String, String> projectFiles = new LinkedHashMap<>(); // 保持插入顺序
        Matcher matcher = FILE_BLOCK_PATTERN.matcher(rawResponse);

        boolean foundAnyFile = false;
        while (matcher.find()) {
            foundAnyFile = true;
            String filePath = matcher.group(1).trim();
            String fileContent = matcher.group(2);

            // 安全处理：标准化路径分隔符，移除可能的绝对路径前缀
            String normalizedPath = filePath
                    .replace('\\', '/') // 统一为 Unix 风格
                    .replaceAll("^/+", ""); // 移除开头的斜杠，防止变成绝对路径

            // 基本校验：跳过明显无效的路径
            if (normalizedPath.isEmpty() || normalizedPath.contains("..")) {
                log.warn("跳过无效或危险的文件路径: {}", filePath);
                continue;
            }

            projectFiles.put(normalizedPath, fileContent);
            log.debug("已解析文件: {}", normalizedPath);
        }

        if (!foundAnyFile) {
            log.warn("AI 响应中未找到任何符合 [FILE_START]/[FILE_END] 格式的文件块。原始响应:\n{}", rawResponse);
            throw new GraphRunnerException("AI 未按指定格式输出文件，请检查提示词或模型行为。");
        }

        log.info("✅ 成功从 AI 响应中解析出 {} 个文件", projectFiles.size());
        return projectFiles;
    }

    private String buildUserPromptForAnythingLLM(String systemName, String prdContent, String archContent) {
        return String.format(
                """
                你是一位世界顶级的全栈软件架构师和工程师。你的任务是根据提供的《需求规格说明书》(PRD) 和《系统架构文档》，为「%s」系统生成一个**完整、可直接运行**的前后端分离项目。
        
                ### 📌 核心指令
                1. **项目结构**：必须包含 `backend/` (Spring Boot 3.x) 和 `frontend/` (Vue 3 + Vite) 两个顶级目录。
                2. **输出格式**：严格按照下方 `[FILE_START]` / `[FILE_END]` 的格式输出每个文件。**不要包含任何其他文字、解释、注释或 Markdown**。
                3. **内容要求**：
                   - **后端**：包含完整的 Maven 项目结构 (`pom.xml`, `Application.java`, `controller/`, `service/`, `entity/`, `dto/`, `repository/`, `application.yml`)。
                   - **前端**：包含完整的 Vue 3 项目结构 (`package.json`, `vite.config.js`, `main.js`, `App.vue`, `components/`, `views/`)。
                   - 所有代码必须严格遵循 PRD 和架构文档中定义的 API 接口、数据模型和业务逻辑。
                   - 配置文件（如 `application.yml`, `package.json`）必须是完整且有效的。
                4. **安全与健壮**：代码需包含基本的异常处理和输入校验。
        
                ### 📄 输入文档
                #### 需求规格说明书 (PRD)
                %s
        
                #### 系统架构文档
                %s
        
                ### 🧾 输出格式示例（你必须严格遵守此格式）
                [FILE_START] backend/pom.xml
                <?xml version="1.0" encoding="UTF-8"?>
                <project ...>
                    ...
                </project>
                [FILE_END]
        
                [FILE_START] backend/src/main/java/com/example/%s/controller/UserController.java
                package com.example.%s.controller;
                ...
                @RestController
                public class UserController {
                    ...
                }
                [FILE_END]
        
                [FILE_START] frontend/src/views/UserListView.vue
                <template>
                  <div>...</div>
                </template>
                <script setup>
                ...
                </script>
                [FILE_END]
        
                ### 🚀 开始生成
                请现在开始，按照上述格式，逐个生成项目所需的所有关键文件。
                """,
                systemName,
                prdContent,
                archContent,
                // 为 Java 包名准备（简单处理，实际可更复杂）
                systemName.toLowerCase().replaceAll("[^a-z0-9]", ""),
                systemName.toLowerCase().replaceAll("[^a-z0-9]", "")
        );
    }
}
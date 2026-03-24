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

        Map<String, String> variables = new HashMap<>();
        variables.put("project.name", systemName);
        // 默认全量生成（兼容旧路径）
        variables.put("required.files", """
        - backend/pom.xml
        - backend/src/main/resources/application.yml
        - frontend/package.json
        - frontend/vite.config.js
        - frontend/src/main.js
        - frontend/src/App.vue
        """);
        return callAnythingLLM(userPrompt, variables, systemName);
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
        // 检查是否包含前后端核心文件
        if (!projectFiles.containsKey("backend/pom.xml")) {
            throw new GraphRunnerException("AI 未生成 backend/pom.xml");
        }
        if (!projectFiles.containsKey("frontend/package.json")) {
            throw new GraphRunnerException("AI 未生成 frontend/package.json");
        }
        if (!projectFiles.containsKey("frontend/vite.config.js")) {
            throw new GraphRunnerException("AI 未生成 frontend/vite.config.js");
        }
        if (!projectFiles.containsKey("frontend/src/main.js")) {
            throw new GraphRunnerException("AI 未生成 frontend/src/main.js");
        }
        if (!projectFiles.containsKey("frontend/src/App.vue")) {
            throw new GraphRunnerException("AI 未生成 frontend/src/App.vue");
        }

        log.info("✅ 成功从 AI 响应中解析出 {} 个文件", projectFiles.size());
        return projectFiles;
    }

    // ===== 新增：按阶段生成 =====
    public Map<String, String> generateBackendFiles(String systemName, String prdContent, String archContent) throws GraphRunnerException {
        String prompt = buildBackendPrompt(systemName, prdContent, archContent);
        Map<String, String> variables = new HashMap<>();
        variables.put("project.name", systemName);
        variables.put("required.files", """
        - backend/pom.xml
        - backend/src/main/resources/application.yml
        - backend/src/main/java/**/controller/*.java
        - backend/src/main/java/**/Application.java
        """);
        String raw = callAnythingLLM(prompt, variables, systemName);
        Map<String, String> files = parseFiles(raw);
        if (!files.containsKey("backend/pom.xml")) {
            throw new GraphRunnerException("后端生成失败：缺少 backend/pom.xml");
        }
        boolean hasController = files.entrySet().stream()
                .anyMatch(e -> e.getKey().startsWith("backend/src/main/java/") &&
                        e.getKey().contains("/controller/") &&
                        e.getValue().contains("@RestController"));
        if (!hasController) {
            throw new GraphRunnerException("后端生成失败：未检测到任何 @RestController 控制器");
        }
        return files.entrySet().stream()
                .filter(e -> e.getKey().startsWith("backend/"))
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), LinkedHashMap::putAll);
    }

    // ===== 工具：调用 AnythingLLM =====
    private String callAnythingLLM(String userPrompt, Map<String, String> variables, String systemName) throws GraphRunnerException {
        String url = String.format(
                "%s/api/v1/workspace/%s/chat",
                coderConfig.getBaseUrl().replaceAll("/+$", ""),
                coderConfig.getWorkspaceSlug()
        );
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("message", userPrompt);
        requestBody.put("max_tokens", 8192);
        requestBody.put("stream", false);
        requestBody.put("mode", "chat");
        requestBody.put("sessionId", "agent-session-" + System.currentTimeMillis());
        requestBody.put("variables", variables);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("Authorization", "Bearer " + coderConfig.getApiKey().trim());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        try {
            log.info("调用 AnythingLLM 编码智能体 | URL: {} | System: {}", url, systemName);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = OBJECT_MAPPER.readTree(response.getBody());
                JsonNode textNode = root.path("textResponse");
                if (!textNode.isMissingNode() && !textNode.asText().isEmpty()) {
                    String code = textNode.asText();
                    log.info("✅ 代码生成成功，长度：{} 字符", code.length());
                    return code;
                }
                throw new GraphRunnerException("AnythingLLM 响应中无有效代码输出: " + response.getBody());
            }
            throw new GraphRunnerException(
                    String.format("调用 AnythingLLM 失败 [%s]: %s", response.getStatusCode(), response.getBody())
            );
        } catch (RestClientException e) {
            log.error("网络异常", e);
            throw new GraphRunnerException("调用 AnythingLLM 失败：" + e.getMessage(), e);
        } catch (Exception e) {
            log.error("解析响应失败", e);
            throw new GraphRunnerException("解析 AnythingLLM 响应失败：" + e.getMessage(), e);
        }
    }

    // ===== 工具：解析 [FILE_START]/[FILE_END] =====
    private Map<String, String> parseFiles(String rawResponse) throws GraphRunnerException {
        if (rawResponse.contains("// 编程智能体未启用")) {
            return new HashMap<>();
        }
        Map<String, String> projectFiles = new LinkedHashMap<>();
        Matcher matcher = FILE_BLOCK_PATTERN.matcher(rawResponse);
        boolean foundAnyFile = false;
        while (matcher.find()) {
            foundAnyFile = true;
            String filePath = matcher.group(1).trim();
            String fileContent = matcher.group(2);
            String normalizedPath = filePath
                    .replace('\\', '/')
                    .replaceAll("^/+", "");
            if (normalizedPath.isEmpty() || normalizedPath.contains("..")) {
                log.warn("跳过无效或危险的文件路径: {}", filePath);
                continue;
            }
            projectFiles.put(normalizedPath, fileContent);
        }
        if (!foundAnyFile) {
            log.warn("AI 响应中未找到任何符合 [FILE_START]/[FILE_END] 格式的文件块。原始响应:\n{}", rawResponse);
            throw new GraphRunnerException("AI 未按指定格式输出文件，请检查提示词或模型行为。");
        }
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
                     - 必须根据「接口定义（RESTful）」章节为**每一条接口**生成对应的 `@RestController` 控制器与方法：
                       - 控制器包路径：`backend/src/main/java/com/example/%s/controller/`
                       - 类命名以业务为维度（如 `OrderController`、`ProductController`）
                       - 方法需严格匹配表格中的「接口路径」与「请求方法」（使用 `@GetMapping/@PostMapping/...`）
                       - 方法参数与响应结构需与表格一致；如缺乏细节，请返回最小可运行的 JSON 示例（`ResponseEntity.ok(Map.of(...))`）
                     - 至少包含一个全局异常处理或基础校验示例，保证项目可运行。
                   - **前端**：包含完整的 Vue 3 项目结构 (`package.json`, `vite.config.js`, `main.js`, `App.vue`, `components/`, `views/`)。上述文件均为必需。
                     - 至少提供一个基础视图，调用后端接口并展示返回数据（可用 `fetch` 或 `axios`）。
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
        
                [FILE_START] frontend/package.json
                {
                  "name": "frontend",
                  "private": true,
                  "version": "0.1.0",
                  "type": "module",
                  "scripts": {
                    "dev": "vite",
                    "build": "vite build",
                    "preview": "vite preview"
                  },
                  "dependencies": {
                    "vue": "^3.4.0"
                  },
                  "devDependencies": {
                    "@vitejs/plugin-vue": "^5.0.0",
                    "vite": "^5.0.0"
                  }
                }
                [FILE_END]

                [FILE_START] backend/src/main/java/com/example/%s/controller/UserController.java
                package com.example.%s.controller;
                ...
                @RestController
                public class UserController {
                    @GetMapping("/api/users")
                    public ResponseEntity<List<Map<String, Object>>> listUsers() {
                        return ResponseEntity.ok(List.of(
                            Map.of("id", 1, "name", "Alice"),
                            Map.of("id", 2, "name", "Bob")
                        ));
                    }
                }
                [FILE_END]
        
                [FILE_START] frontend/src/views/UserListView.vue
                <template>
                  <div>
                    <h2>Users</h2>
                    <ul>
                      <li v-for="u in users" :key="u.id">{{ u.name }}</li>
                    </ul>
                  </div>
                </template>
                <script setup>
                import { ref, onMounted } from 'vue';
                const users = ref([]);
                onMounted(async () => {
                  const res = await fetch('/api/users');
                  users.value = await res.json();
                });
                </script>
                [FILE_END]
        
                ### 🚀 开始生成
                【重要】你必须一次性输出所有文件内容，不得分多次回复。本次响应将被程序解析，任何解释文字都会导致失败。
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

    // ===== 阶段化提示词 =====
    private String buildBackendPrompt(String systemName, String prdContent, String archContent) {
        return String.format("""
                你是一名后端资深工程师。基于以下 PRD 与 架构文档，为「%s」构建 Spring Boot 3.x 后端项目。
                要求：
                - 项目结构：backend/ 顶级目录，包含 pom.xml、Application.java、controller、service、entity、dto、repository、application.yml。
                - 严格根据架构文档的「接口定义（RESTful）」逐条生成 @RestController 控制器与方法，路径与请求方法必须一致。
                - 方法入参/出参严格遵循接口表；如信息缺失，返回最小可运行 JSON 示例。
                - 仅按如下格式输出，逐文件包裹：
                [FILE_START] <相对路径>
                <文件内容>
                [FILE_END]

                【输入文档 - PRD】
                %s

                【输入文档 - 架构】
                %s
                """, systemName, prdContent, archContent);
    }

    // 前端生成逻辑已移至 FrontendGenerationService
}

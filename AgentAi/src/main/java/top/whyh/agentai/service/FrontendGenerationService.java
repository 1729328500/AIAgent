package top.whyh.agentai.service;

import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class FrontendGenerationService {
    private final CoderAgentConfig coderConfig;
    private final RestTemplate restTemplate = new RestTemplate();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern FILE_BLOCK_PATTERN = Pattern.compile("\\[FILE_START\\]\\s*(.+?)\\s*\\n(.*?)\\s*\\[FILE_END\\]", Pattern.DOTALL | Pattern.MULTILINE);

    public Map<String, String> generateFrontendFiles(String systemName, String prdContent, String archContent, Map<String, String> backendFiles) throws GraphRunnerException {
        String apiSpec = extractApiSpecFromBackend(backendFiles);
        String prompt = buildFrontendPrompt(systemName, prdContent, archContent, apiSpec);
        Map<String, String> variables = new HashMap<>();
        variables.put("project.name", systemName);
        variables.put("required.files", """
        - frontend/package.json
        - frontend/vite.config.js
        - frontend/src/main.js
        - frontend/src/App.vue
        - frontend/src/**/*
        """);
        String raw = callAnythingLLM(prompt, variables, systemName);
        Map<String, String> files = parseFiles(raw);
        if (!files.containsKey("frontend/package.json")
                || !files.containsKey("frontend/vite.config.js")
                || !files.containsKey("frontend/src/main.js")
                || !files.containsKey("frontend/src/App.vue")) {
            throw new GraphRunnerException("前端生成失败：缺少必需的前端文件");
        }
        return files.entrySet().stream()
                .filter(e -> e.getKey().startsWith("frontend/"))
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), LinkedHashMap::putAll);
    }

    private String callAnythingLLM(String userPrompt, Map<String, String> variables, String systemName) throws GraphRunnerException {
        String url = String.format("%s/api/v1/workspace/%s/chat",
                coderConfig.getBaseUrl().replaceAll("/+$", ""),
                coderConfig.getWorkspaceSlug());
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
            log.info("调用 AnythingLLM 前端智能体 | URL: {} | System: {}", url, systemName);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = OBJECT_MAPPER.readTree(response.getBody());
                JsonNode textNode = root.path("textResponse");
                if (!textNode.isMissingNode() && !textNode.asText().isEmpty()) {
                    String code = textNode.asText();
                    log.info("代码生成成功，长度：{} 字符", code.length());
                    return code;
                }
                throw new GraphRunnerException("AnythingLLM 响应中无有效代码输出: " + response.getBody());
            }
            throw new GraphRunnerException(String.format("调用 AnythingLLM 失败 [%s]: %s", response.getStatusCode(), response.getBody()));
        } catch (RestClientException e) {
            log.error("网络异常", e);
            throw new GraphRunnerException("调用 AnythingLLM 失败：" + e.getMessage(), e);
        } catch (Exception e) {
            log.error("解析响应失败", e);
            throw new GraphRunnerException("解析 AnythingLLM 响应失败：" + e.getMessage(), e);
        }
    }

    private Map<String, String> parseFiles(String rawResponse) throws GraphRunnerException {
        Map<String, String> projectFiles = new LinkedHashMap<>();
        Matcher matcher = FILE_BLOCK_PATTERN.matcher(rawResponse);
        boolean foundAnyFile = false;
        while (matcher.find()) {
            foundAnyFile = true;
            String filePath = matcher.group(1).trim();
            String fileContent = matcher.group(2);
            String normalizedPath = filePath.replace('\\', '/').replaceAll("^/+", "");
            if (normalizedPath.isEmpty() || normalizedPath.contains("..")) {
                continue;
            }
            projectFiles.put(normalizedPath, fileContent);
        }
        if (!foundAnyFile) {
            throw new GraphRunnerException("AI 未按指定格式输出文件，请检查提示词或模型行为。");
        }
        return projectFiles;
    }

    private String extractApiSpecFromBackend(Map<String, String> backendFiles) {
        StringBuilder sb = new StringBuilder();
        sb.append("后端已生成的接口如下：\n");
        backendFiles.forEach((path, content) -> {
            if (path.startsWith("backend/src/main/java/") && path.contains("/controller/")) {
                Pattern classMapping = Pattern.compile("@RequestMapping\\(\"([^\"]+)\"\\)");
                Pattern methodGet = Pattern.compile("@GetMapping\\(\"([^\"]*)\"\\)");
                Pattern methodPost = Pattern.compile("@PostMapping\\(\"([^\"]*)\"\\)");
                Pattern methodPut = Pattern.compile("@PutMapping\\(\"([^\"]*)\"\\)");
                Pattern methodDelete = Pattern.compile("@DeleteMapping\\(\"([^\"]*)\"\\)");
                Matcher cm = classMapping.matcher(content);
                String base = "";
                if (cm.find()) base = cm.group(1);
                Matcher mg = methodGet.matcher(content);
                while (mg.find()) sb.append("- GET ").append(base).append(mg.group(1)).append("\n");
                Matcher mp = methodPost.matcher(content);
                while (mp.find()) sb.append("- POST ").append(base).append(mp.group(1)).append("\n");
                Matcher mu = methodPut.matcher(content);
                while (mu.find()) sb.append("- PUT ").append(base).append(mu.group(1)).append("\n");
                Matcher md = methodDelete.matcher(content);
                while (md.find()) sb.append("- DELETE ").append(base).append(md.group(1)).append("\n");
            }
        });
        return sb.toString();
    }

    private String buildFrontendPrompt(String systemName, String prdContent, String archContent, String apiSpec) {
        return String.format("""
                你是一名前端资深工程师。基于后端已实现的接口，为「%s」构建 Vue 3 + Vite 前端。
                要求：
                - 项目结构：frontend/ 顶级目录，包含 package.json、vite.config.js、src/main.js、src/App.vue、components、views。
                - 至少包含一个视图，实际调用后端接口并展示数据。
                - 使用 fetch 或 axios 请求后端；合理封装 API 调用。
                - 仅按如下格式输出，逐文件包裹：
                [FILE_START] <相对路径>
                <文件内容>
                [FILE_END]

                【后端可用接口】
                %s

                【输入文档 - PRD】
                %s

                【输入文档 - 架构】
                %s
                """, systemName, apiSpec, prdContent, archContent);
    }
}

package top.whyh.agentai.service.codegen;

import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BackendControllerGenerator {
    private final LlmClient llmClient;
    private final FileBlockParser parser;

    public Map<String, String> generateBackendControllers(String systemName, String prdContent, String archContent) throws GraphRunnerException {
        String basePackage = "com." + systemName.toLowerCase().replaceAll("[^a-z0-9]", "");
        String prompt = String.format("""
                你是一名后端工程师，为「%s」系统生成 Spring Boot Controller。
                要求：
                - **所有文件必须放在 backend/ 目录下**！
                - **必须使用包名: %s.controller**
                - 必须为每一项核心功能生成对应的 @RestController。
                - 严格按以下格式输出，不要包含任何其他文字：
                [FILE_START] backend/src/main/java/%s/controller/UserController.java
                package %s.controller;
                ...
                [FILE_END]

                【PRD】
                %s
                【架构文档】
                %s
                """, systemName, basePackage, basePackage.replace(".", "/"), basePackage, prdContent, archContent);
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("project.name", systemName);
        variables.put("required.files", """
        - backend/src/main/java/**/controller/*.java
        """);
        String raw = llmClient.call(systemName, prompt, variables);
        Map<String, String> files = parser.parse(raw);
        boolean hasController = files.entrySet().stream()
                .anyMatch(e -> e.getKey().startsWith("backend/src/main/java/") &&
                        e.getKey().contains("/controller/") &&
                        e.getValue().contains("@RestController"));
        if (!hasController) {
            throw new GraphRunnerException("未检测到任何 @RestController 控制器");
        }
        Map<String, String> result = new LinkedHashMap<>();
        files.forEach((k, v) -> { if (k.startsWith("backend/")) result.put(k, v); });
        return result;
    }
}

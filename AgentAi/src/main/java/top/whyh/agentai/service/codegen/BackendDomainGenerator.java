package top.whyh.agentai.service.codegen;

import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BackendDomainGenerator {
    private final LlmClient llmClient;
    private final FileBlockParser parser;

    public Map<String, String> generateBackendDomainLayer(String systemName, String prdContent, String archContent) throws GraphRunnerException {
        String basePackage = "com." + systemName.toLowerCase().replaceAll("[^a-z0-9]", "");
        String prompt = String.format("""
                你是一名后端工程师，为「%s」生成后端领域层。
                要求：
                - **所有文件必须放在 backend/ 目录下**！
                - **项目包名必须是: %s**
                - 必须包含以下层次：
                  1. **Entity**: 数据库实体类，包名 `%s.entity`
                  2. **Repository**: Spring Data JPA 接口，包名 `%s.repository`
                  3. **Service**: 业务逻辑层，**必须包含接口（%s.service）和实现类（%s.service.impl）**。
                     - **必须包含 AuthService 接口及其实现类 AuthServiceImpl**，处理登录认证逻辑。
                  4. **DTO**: 数据传输对象（Request/Response），包名 `%s.dto`。
                     - **必须为所有核心操作生成对应的 Request/Response DTO 类**（如 LoginRequest, UserCreateRequest, BookResponse 等）。
                - 严格按以下格式输出，不要包含任何其他文字：
                [FILE_START] backend/src/main/java/%s/service/AuthService.java
                package %s.service;
                ...
                [FILE_END]
                
                [FILE_START] backend/src/main/java/%s/dto/LoginRequest.java
                package %s.dto;
                ...
                [FILE_END]

                【PRD】
                %s
                【架构】
                %s
                """, systemName, basePackage, basePackage, basePackage, basePackage, basePackage, basePackage, basePackage.replace(".", "/"), basePackage, basePackage.replace(".", "/"), basePackage, prdContent, archContent);
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("project.name", systemName);
        variables.put("required.files", """
        - backend/src/main/java/**/service/*.java
        - backend/src/main/java/**/entity/*.java
        - backend/src/main/java/**/dto/*.java
        - backend/src/main/java/**/repository/*.java
        """);
        String raw = llmClient.call(systemName, prompt, variables);
        Map<String, String> files = parser.parse(raw);
        
        if (files.isEmpty()) {
            throw new GraphRunnerException("后端领域层生成失败：AI 未返回任何有效文件");
        }
        
        Map<String, String> result = new LinkedHashMap<>();
        files.forEach((k, v) -> { if (k.startsWith("backend/")) result.put(k, v); });
        return result;
    }
}

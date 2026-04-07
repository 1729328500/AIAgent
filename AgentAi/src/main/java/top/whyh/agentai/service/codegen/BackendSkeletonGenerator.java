package top.whyh.agentai.service.codegen;

import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BackendSkeletonGenerator {
    private final LlmClient llmClient;
    private final FileBlockParser parser;

    public Map<String, String> generateBackendSkeleton(String systemName, String prdContent, String archContent) throws GraphRunnerException {
        String basePackage = "com." + systemName.toLowerCase().replaceAll("[^a-z0-9]", "");
        String prompt = String.format("""
                你是一名后端架构师，为「%s」生成后端项目骨架。
                要求：
                - **所有文件必须放在 backend/ 目录下**！
                - **项目包名（Package Name）必须是: %s**
                - 必须包含以下核心文件：
                  1. backend/pom.xml (必须包含 Spring Boot 3.x 依赖、Spring Security、JWT、Redis 等项目坐标)
                  2. backend/src/main/java/%s/%sApplication.java (启动类)
                  3. backend/src/main/resources/application.yml (配置端口、数据库连接、Redis、JWT 密钥等)
                  4. backend/src/main/java/%s/config/SecurityConfig.java (Spring Security 配置，处理权限和认证规则)
                  5. backend/src/main/java/%s/config/CorsConfig.java (跨域配置，允许前端访问后端接口)
                - 严格按以下格式输出，不要包含任何其他文字：
                [FILE_START] backend/pom.xml
                <?xml version="1.0" encoding="UTF-8"?>
                ...
                [FILE_END]

                [FILE_START] backend/src/main/java/%s/%sApplication.java
                package %s;
                ...
                [FILE_END]

                [FILE_START] backend/src/main/java/%s/config/SecurityConfig.java
                package %s.config;
                ...
                [FILE_END]

                [FILE_START] backend/src/main/java/%s/config/CorsConfig.java
                package %s.config;
                import org.springframework.context.annotation.Configuration;
                import org.springframework.web.servlet.config.annotation.CorsRegistry;
                import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
                @Configuration
                public class CorsConfig implements WebMvcConfigurer {
                    @Override
                    public void addCorsMappings(CorsRegistry registry) {
                        registry.addMapping("/**").allowedOriginPatterns("*").allowedMethods("*").allowedHeaders("*").allowCredentials(true);
                    }
                }
                [FILE_END]

                【PRD】
                %s
                【架构】
                %s
                """, systemName, basePackage, basePackage.replace(".", "/"), systemName, basePackage.replace(".", "/"), basePackage.replace(".", "/"), basePackage.replace(".", "/"), systemName, basePackage, basePackage.replace(".", "/"), basePackage, basePackage.replace(".", "/"), basePackage, prdContent, archContent);
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("project.name", systemName);
        variables.put("required.files", """
        - backend/pom.xml
        - backend/src/main/resources/application.yml
        - backend/src/main/java/**/Application.java
        """);
        String raw = llmClient.call(systemName, prompt, variables);
        Map<String, String> files = parser.parse(raw);

        if (files.isEmpty()) {
            throw new GraphRunnerException("后端骨架生成失败：AI 未返回任何有效文件");
        }

        // 【新增】即时验证必需文件
        List<String> requiredFiles = List.of(
            "backend/pom.xml",
            "backend/src/main/resources/application.yml"
        );

        List<String> missing = new ArrayList<>();
        for (String required : requiredFiles) {
            boolean found = files.keySet().stream().anyMatch(k -> k.equals(required));
            if (!found) {
                missing.add(required);
            }
        }

        // 【新增】如果缺少必需文件，立即重试一次
        if (!missing.isEmpty()) {
            log.warn("后端骨架缺少必需文件: {}, 尝试补充生成...", missing);
            String supplementPrompt = String.format("""
                    系统「%s」的后端骨架缺少以下必需文件，请立即生成：
                    %s

                    项目包名: %s

                    严格按以下格式输出：
                    [FILE_START] 文件路径
                    文件内容
                    [FILE_END]

                    不要输出任何解释文字或 Markdown 标签。
                    """, systemName, String.join("\n", missing), basePackage);

            String supplementRaw = llmClient.call(systemName, supplementPrompt, variables);
            Map<String, String> supplementFiles = parser.parse(supplementRaw);
            files.putAll(supplementFiles);
            log.info("补充生成完成，新增文件数: {}", supplementFiles.size());
        }

        Map<String, String> result = new LinkedHashMap<>();
        files.forEach((k, v) -> { if (k.startsWith("backend/")) result.put(k, v); });
        return result;
    }
}

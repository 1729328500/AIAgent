package top.whyh.agentai.service.codegen;

import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
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
        if (!files.containsKey("backend/pom.xml")) {
            throw new GraphRunnerException("后端骨架缺少 backend/pom.xml");
        }
        boolean hasApp = files.keySet().stream().anyMatch(p -> p.startsWith("backend/src/main/java/") && p.endsWith("Application.java"));
        if (!hasApp) {
            throw new GraphRunnerException("后端骨架缺少 Application.java");
        }
        if (!files.containsKey("backend/src/main/resources/application.yml")) {
            throw new GraphRunnerException("后端骨架缺少 application.yml");
        }
        Map<String, String> result = new LinkedHashMap<>();
        files.forEach((k, v) -> { if (k.startsWith("backend/")) result.put(k, v); });
        return result;
    }
}

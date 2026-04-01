package top.whyh.agentai.service.codegen;

import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FrontendApiGenerator {
    private final LlmClient llmClient;
    private final FileBlockParser parser;
    private final ApiSpecExtractor apiSpecExtractor;

    public Map<String, String> generateFrontendApiUtils(String systemName, String prdContent, String archContent, Map<String, String> backendFiles) throws GraphRunnerException {
        String apiSpec = apiSpecExtractor.extract(backendFiles);
        String prompt = String.format("""
                你是一名前端工程师，为「%s」生成前端 API 封装模块，按业务拆分到 frontend/src/api/*.js 或 *.ts。
                要求：
                - **所有文件必须放在 frontend/ 目录下**！
                - 基于后端接口封装 get/post/put/delete 调用
                - 导出可复用的方法
                - 严格按以下格式输出，不要包含任何其他文字：
                [FILE_START] frontend/src/api/user.js
                import axios from 'axios';
                ...
                [FILE_END]

                【后端接口】
                %s
                【PRD】
                %s
                【架构】
                %s
                """, systemName, apiSpec, prdContent, archContent);
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("project.name", systemName);
        variables.put("required.files", """
        - frontend/src/api/**/*.js
        - frontend/src/api/**/*.ts
        """);
        String raw = llmClient.call(systemName, prompt, variables);
        Map<String, String> files = parser.parse(raw);
        boolean hasApi = files.keySet().stream().anyMatch(p -> p.startsWith("frontend/src/api/"));
        if (!hasApi) {
            throw new GraphRunnerException("未检测到任何前端 API 封装文件");
        }
        Map<String, String> result = new LinkedHashMap<>();
        files.forEach((k, v) -> { if (k.startsWith("frontend/")) result.put(k, v); });
        return result;
    }
}

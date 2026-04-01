package top.whyh.agentai.service.codegen;

import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FrontendViewsGenerator {
    private final LlmClient llmClient;
    private final FileBlockParser parser;
    private final ApiSpecExtractor apiSpecExtractor;

    public Map<String, String> generateFrontendViews(String systemName, String prdContent, String archContent, Map<String, String> backendFiles) throws GraphRunnerException {
        String apiSpec = apiSpecExtractor.extract(backendFiles);
        String prompt = String.format("""
                你是一名前端工程师，为「%s」生成前端视图文件，至少包含一个页面从后端接口拉取数据并展示。
                要求：
                - **所有文件必须放在 frontend/ 目录下**！
                - 严格按以下格式输出，不要包含任何其他文字：
                [FILE_START] frontend/src/views/UserListView.vue
                <template>...</template>
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
        - frontend/src/views/**/*.vue
        """);
        String raw = llmClient.call(systemName, prompt, variables);
        Map<String, String> files = parser.parse(raw);
        boolean hasView = files.keySet().stream().anyMatch(p -> p.startsWith("frontend/src/views/") && p.endsWith(".vue"));
        if (!hasView) {
            throw new GraphRunnerException("未检测到任何前端视图文件");
        }
        Map<String, String> result = new LinkedHashMap<>();
        files.forEach((k, v) -> { if (k.startsWith("frontend/")) result.put(k, v); });
        return result;
    }
}

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
public class FrontendSkeletonGenerator {
    private final LlmClient llmClient;
    private final FileBlockParser parser;

    public Map<String, String> generateFrontendSkeleton(String systemName, String prdContent, String archContent) throws GraphRunnerException {
        String prompt = String.format("""
                你是一名前端架构师，为「%s」生成前端骨架（Vue3 + Vite）。
                要求：
                - **所有文件必须放在 frontend/ 目录下**！
                - **必须生成以下核心文件**（不可遗漏）：
                  1. frontend/package.json (包含 vue, vite, axios, vue-router, pinia 等基本依赖)
                  2. frontend/vite.config.js (配置代理 /api 映射到后端 http://localhost:8080)
                  3. frontend/src/main.js (挂载 Vue 实例、注册路由和状态管理)
                  4. frontend/src/App.vue (根组件，包含 <router-view />)
                  5. frontend/src/router/index.js (Vue Router 配置，包含登录页、首页等基础路由)
                  6. frontend/src/utils/axios.js (自定义 Axios 实例，配置基础 URL 为空字符串（由 Vite 代理处理）或直接指向后端，配置请求/响应拦截器处理 JWT Token)
                - 严格按以下格式输出，不要包含任何其他文字：
                [FILE_START] frontend/vite.config.js
                import { defineConfig } from 'vite';
                import vue from '@vitejs/plugin-vue';
                export default defineConfig({
                  plugins: [vue()],
                  server: {
                    proxy: {
                      '/api': { target: 'http://localhost:8080', changeOrigin: true }
                    }
                  }
                });
                [FILE_END]

                【PRD】
                %s
                【架构】
                %s
                """, systemName, prdContent, archContent);
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("project.name", systemName);
        variables.put("required.files", """
        - frontend/package.json
        - frontend/vite.config.js
        - frontend/src/main.js
        - frontend/src/App.vue
        - frontend/src/router/index.js
        - frontend/src/utils/axios.js
        """);
        String raw = llmClient.call(systemName, prompt, variables);
        Map<String, String> files = parser.parse(raw);

        // 移除硬校验，交由 CodeReview 阶段处理修复
        if (files.isEmpty()) {
             // 只有在完全没有生成文件时才抛出异常（LLM 异常）
            throw new GraphRunnerException("前端骨架生成失败：AI 未返回任何有效文件");
        }

        // 【新增】即时验证必需文件
        List<String> requiredFiles = List.of(
            "frontend/package.json",
            "frontend/vite.config.js",
            "frontend/src/main.js",
            "frontend/src/App.vue"
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
            log.warn("前端骨架缺少必需文件: {}, 尝试补充生成...", missing);
            String supplementPrompt = String.format("""
                    系统「%s」的前端骨架缺少以下必需文件，请立即生成：
                    %s

                    严格按以下格式输出：
                    [FILE_START] 文件路径
                    文件内容
                    [FILE_END]

                    不要输出任何解释文字或 Markdown 标签。
                    """, systemName, String.join("\n", missing));

            String supplementRaw = llmClient.call(systemName, supplementPrompt, variables);
            Map<String, String> supplementFiles = parser.parse(supplementRaw);
            files.putAll(supplementFiles);
            log.info("补充生成完成，新增文件数: {}", supplementFiles.size());
        }

        Map<String, String> result = new LinkedHashMap<>();
        files.forEach((k, v) -> { if (k.startsWith("frontend/")) result.put(k, v); });
        return result;
    }
}

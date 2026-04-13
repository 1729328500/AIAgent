package top.whyh.agentai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import top.whyh.agentai.result.SandboxResult;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * E2B 沙箱部署服务：将 AI 生成的前端项目部署到 E2B 沙箱并返回预览地址。
 * 仅部署前端文件（frontend/ 目录下），避免额度浪费。
 */
@Service
@Slf4j
public class SandboxDeploymentService {

    private static final String E2B_API_BASE = "https://api.e2b.app";
    /** 沙箱超时：30 分钟（成本控制） */
    private static final int SANDBOX_TIMEOUT_SECONDS = 1800;
    /** Vite 默认端口 */
    private static final int FRONTEND_PORT = 5173;
    /** E2B 基础模板（内含 Node.js/npm） */
    private static final String TEMPLATE_ID = "base";
    /** 最多上传文件数（成本控制，优先核心文件） */
    private static final int MAX_UPLOAD_FILES = 60;

    /** 优先上传的核心文件关键字（按优先级排序） */
    private static final List<String> PRIORITY_KEYWORDS = List.of(
            "package.json", "vite.config", "index.html", "main.js", "main.ts",
            "App.vue", "router/index", "axios", "request.js", "request.ts",
            ".env", "tailwind", "app.css", "main.css"
    );

    @Value("${e2b.api-key:}")
    private String apiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public SandboxDeploymentService(ObjectMapper objectMapper) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }

    /**
     * 将前端项目部署到 E2B 沙箱。
     *
     * @param systemName   系统名称（用于 metadata 标记）
     * @param projectFiles 生成的完整项目文件 Map（路径 → 内容）
     * @return 部署结果，包含预览 URL 或错误信息
     */
    public SandboxResult deployFrontend(String systemName, Map<String, String> projectFiles) {
        if (!isConfigured()) {
            return SandboxResult.failure("E2B API Key 未配置，请在 application.yml 中设置 e2b.api-key");
        }

        // 提取前端文件（去掉 frontend/ 前缀）
        Map<String, String> frontendFiles = projectFiles.entrySet().stream()
                .filter(e -> e.getKey().startsWith("frontend/"))
                .collect(Collectors.toMap(
                        e -> e.getKey().substring("frontend/".length()),
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        if (frontendFiles.isEmpty()) {
            return SandboxResult.failure("未找到前端文件（frontend/ 目录为空）");
        }

        log.info("开始部署 [{}] 到 E2B 沙箱 | 前端文件数: {}", systemName, frontendFiles.size());

        String sandboxId = null;
        try {
            // 1. 创建沙箱
            sandboxId = createSandbox(systemName);
            log.info("E2B 沙箱已创建 | sandboxId: {}", sandboxId);

            // 2. 上传文件（优先核心文件，控制数量）
            List<Map.Entry<String, String>> prioritizedFiles = prioritizeFiles(frontendFiles);
            uploadFiles(sandboxId, prioritizedFiles);

            // 3. 启动 npm 安装 + Vite 开发服务器（后台运行）
            startDevServer(sandboxId);

            // 4. 构建预览 URL
            String previewUrl = buildPortUrl(sandboxId, FRONTEND_PORT);
            log.info("E2B 沙箱部署成功 | sandboxId: {} | previewUrl: {}", sandboxId, previewUrl);

            return SandboxResult.success(sandboxId, previewUrl, SANDBOX_TIMEOUT_SECONDS);

        } catch (HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            log.error("E2B API 请求失败 | status: {} | body: {}", e.getStatusCode(), body);
            return SandboxResult.failure("E2B API 错误 [" + e.getStatusCode() + "]: " + body);
        } catch (Exception e) {
            log.error("E2B 沙箱部署失败 | sandboxId: {} | 错误: {}", sandboxId, e.getMessage(), e);
            return SandboxResult.failure("沙箱部署失败: " + e.getMessage());
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private String createSandbox(String systemName) throws Exception {
        HttpHeaders headers = buildHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("templateID", TEMPLATE_ID);
        body.put("timeout", SANDBOX_TIMEOUT_SECONDS);
        body.put("metadata", Map.of("project", systemName, "source", "agentai"));

        @SuppressWarnings("unchecked")
        ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>)
                restTemplate.exchange(
                        E2B_API_BASE + "/sandboxes", // 移除了 /v1
                        HttpMethod.POST,
                        new HttpEntity<>(body, headers),
                        Map.class
                );

        if (response.getBody() == null || !response.getBody().containsKey("sandboxID")) {
            throw new RuntimeException("创建沙箱响应异常，缺少 sandboxID 字段：" + response.getBody());
        }

        return String.valueOf(response.getBody().get("sandboxID"));
    }

    /** 按优先级排序文件，优先上传核心文件 */
    private List<Map.Entry<String, String>> prioritizeFiles(Map<String, String> files) {
        List<Map.Entry<String, String>> all = new ArrayList<>(files.entrySet());
        all.sort((a, b) -> {
            int pa = getPriority(a.getKey());
            int pb = getPriority(b.getKey());
            return Integer.compare(pa, pb);
        });
        if (all.size() > MAX_UPLOAD_FILES) {
            log.warn("前端文件总数 {} 超过上限 {}，将跳过低优先级文件", all.size(), MAX_UPLOAD_FILES);
            return all.subList(0, MAX_UPLOAD_FILES);
        }
        return all;
    }

    private int getPriority(String path) {
        for (int i = 0; i < PRIORITY_KEYWORDS.size(); i++) {
            if (path.contains(PRIORITY_KEYWORDS.get(i))) return i;
        }
        // 视图文件次高优先级，其余最低
        if (path.endsWith(".vue")) return PRIORITY_KEYWORDS.size();
        if (path.endsWith(".js") || path.endsWith(".ts")) return PRIORITY_KEYWORDS.size() + 1;
        return PRIORITY_KEYWORDS.size() + 2;
    }

    private void uploadFiles(String sandboxId, List<Map.Entry<String, String>> files) {
        int success = 0;
        for (Map.Entry<String, String> entry : files) {
            try {
                String remotePath = "/workspace/" + entry.getKey();
                uploadFile(sandboxId, remotePath, entry.getValue());
                success++;
            } catch (Exception e) {
                log.warn("上传文件失败 [{}]: {}", entry.getKey(), e.getMessage());
            }
        }
        log.info("文件上传完成 | 成功: {}/{}", success, files.size());
    }

    private void uploadFile(String sandboxId, String remotePath, String content) {
        HttpHeaders headers = buildHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

// 注意：新端点使用 JSON body 来传递路径和内容，而不是 query param 和 raw body
        Map<String, Object> uploadBody = new HashMap<>();
        uploadBody.put("path", remotePath); // 使用原始路径，无需在URL中编码
        uploadBody.put("content", content);

        restTemplate.exchange(
                E2B_API_BASE + "/sandboxes/" + sandboxId + "/filesystem/write",
                HttpMethod.POST,
                new HttpEntity<>(uploadBody, headers), // 发送 JSON body
                String.class
        );
    }

    private void startDevServer(String sandboxId) throws Exception {
        HttpHeaders headers = buildHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // npm install 完成后启动 Vite dev server，绑定 0.0.0.0 以便 E2B 端口转发
        String cmd = String.format(
                "cd /workspace && npm install --legacy-peer-deps --prefer-offline 2>&1 | tail -5 " +
                "&& npm run dev -- --host 0.0.0.0 --port %d", FRONTEND_PORT);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cmd", cmd);
        body.put("cwd", "/workspace");
        // timeoutMs: 仅等待 15 秒即返回，进程继续在沙箱中后台运行
        body.put("timeoutMs", 15000);

        @SuppressWarnings("unchecked")
        ResponseEntity<Map<String, Object>> response = (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>)
                restTemplate.exchange(
                        E2B_API_BASE + "/sandboxes/" + sandboxId + "/processes", // 移除了 /v1
                        HttpMethod.POST,
                        new HttpEntity<>(body, headers),
                        Map.class
                );

        String processId = response.getBody() != null ?
                String.valueOf(response.getBody().getOrDefault("processID", "unknown")) : "unknown";
        log.info("开发服务器进程已启动 | sandboxId: {} | processId: {}", sandboxId, processId);
    }

    /** E2B 端口转发 URL 格式：https://{port}-{sandboxId}.e2b.dev */
    private String buildPortUrl(String sandboxId, int port) {
        return "https://" + port + "-" + sandboxId + ".e2b.dev";
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", apiKey);
        return headers;
    }

    private boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}

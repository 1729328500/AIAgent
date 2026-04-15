package top.whyh.agentai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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

    @Value("${e2b.proxy-url:http://localhost:3002}")
    private String proxyUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public SandboxDeploymentService(ObjectMapper objectMapper) {
        // npm install 最多 3 分钟 + vite 启动最多 60 秒，总超时设为 5 分钟
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(300_000);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = objectMapper;
    }

    /**
     * 将前端项目通过 Node.js 代理服务部署到 E2B 沙箱。
     *
     * @param systemName   系统名称（用于 metadata 标记）
     * @param projectFiles 生成的完整项目文件 Map（路径 → 内容）
     * @return 部署结果，包含预览 URL 或错误信息
     */
    public String getProxyUrl() {
        return proxyUrl;
    }

    public SandboxResult deployFrontend(String systemName, Map<String, String> projectFiles) {
        log.info("开始通过代理服务部署 [{}] 到 E2B 沙箱", systemName);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("systemName", systemName);
            requestBody.put("projectFiles", projectFiles);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    proxyUrl + "/deploy-frontend",
                    new HttpEntity<>(requestBody, headers),
                    Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                if (Boolean.TRUE.equals(body.get("success"))) {
                    String sandboxId = (String) body.get("sandboxId");
                    String previewUrl = (String) body.get("previewUrl");
                    Integer timeoutSeconds = (Integer) body.get("timeoutSeconds");
                    log.info("E2B 沙箱部署成功 (via Proxy) | sandboxId: {} | previewUrl: {}", sandboxId, previewUrl);
                    return SandboxResult.success(sandboxId, previewUrl, timeoutSeconds != null ? timeoutSeconds : 1800);
                } else {
                    String error = (String) body.get("error");
                    return SandboxResult.failure("代理服务返回错误: " + error);
                }
            } else {
                return SandboxResult.failure("调用代理服务失败，HTTP 状态码: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("调用 E2B 代理服务失败 | 错误: {}", e.getMessage(), e);
            return SandboxResult.failure("无法连接到 E2B 代理服务: " + e.getMessage() + "。请确保 Node.js 代理服务已启动。");
        }
    }

    // ─── Private helpers (已废弃，保留占位或删除) ──────────────────────────────────────────────────────

    private boolean isConfigured() {
        return true; // 代理服务自己持有 API Key
    }
}

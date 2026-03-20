package top.whyh.agentai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "agent.ai.coder")
public class CoderAgentConfig {
    private boolean enabled = true;

    // --- 移除百炼相关字段 ---
    // private String appId;
    // private String region;
    // private String accessKeyId;
    // private String accessKeySecret;

    // --- 新增 AnythingLLM 相关字段 ---
    /** AnythingLLM 服务器地址 */
    private String baseUrl = "http://localhost:3001";
    /** AnythingLLM 工作区的 Slug */
    private String workspaceSlug = "82ee944e-b422-48e8-89e4-7c6d3a1e317a"; // 请替换为您的实际slug
    /** AnythingLLM 的 API Key */
    private String apiKey = "EHAAM3W-KGBMB59-GYSHVA-PVQ461F";

    private int timeoutMs = 30000;
    private int maxRetry = 2;
}
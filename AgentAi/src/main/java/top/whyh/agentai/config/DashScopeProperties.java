package top.whyh.agentai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 通义千问独立配置类（绑定spring.ai.dashscope前缀）
 */
// 新增：单独的DashScope配置类（关键！绑定spring.ai.dashscope前缀）
@Component
@ConfigurationProperties(prefix = "spring.ai.dashscope")
@Data
public class DashScopeProperties {
    private String apiKey; // 对应spring.ai.dashscope.api-key
    private String model = "qwen-plus";
    private Float temperature = 0.4F;
    private Integer maxTokens = 8000;
    private Float topP = 0.9F;
    private Integer maxRetry = 3;
}

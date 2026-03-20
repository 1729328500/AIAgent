// top/whyh/agentai/config/AgentOutputConfig.java

package top.whyh.agentai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "agent-output") // 👈 前缀改为 "agent-output"
public class AgentOutputConfig {

    private String root = "D:/AgentAI_Output"; // 对应 yml 中的 agent-output.root

    public String getPrdStoragePath() {
        return root + "/PRD_Documents";
    }

    public String getArchStoragePath() {
        return root + "/Architecture_Documents";
    }

    public String getGeneratedProjectsPath() {
        return root + "/Generated_Projects";
    }
}
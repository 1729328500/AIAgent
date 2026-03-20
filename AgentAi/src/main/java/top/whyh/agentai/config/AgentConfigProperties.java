package top.whyh.agentai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

/**
 * 智能体通用配置类
 */
@Component
@ConfigurationProperties(prefix = "agent.ai") // 业务配置前缀
@Data
public class AgentConfigProperties {

    // ========== 1. 文档存储配置 ==========
    @NestedConfigurationProperty
    private DocumentStorageConfig documentStorage = new DocumentStorageConfig();
    private DocumentStorageConfig codeStorage = new DocumentStorageConfig(); // 新增！

    // ========== 2. 业务规则配置 ==========
    private RequirementAgentConfig requirement = new RequirementAgentConfig();
    private ArchitectAgentConfig architect = new ArchitectAgentConfig();

    private CoderAgentConfig coder = new CoderAgentConfig();

    // ========== 3. 通义千问配置（单独配置类，绑定spring.ai.dashscope） ==========
    // 注意：DashScopeConfig单独用@Component + @ConfigurationProperties绑定spring.ai.dashscope
    // 避免嵌套导致的绑定失败

    /**
     * 文档存储通用配置
     */
    @Data
    public static class DocumentStorageConfig {
        private String path = "${user.dir}/generated_docs";
        private String suffix = ".md";
        private String encoding = "UTF-8";
        private boolean overwrite = true;
        private int maxFileNameLength = 50;
    }

    /**
     * 需求分析智能体专属配置
     */
    @Data
    public static class RequirementAgentConfig {
        private String[] requiredSections = {
                "## 需求背景与目标",
                "## 目标用户与核心场景",
                "## 核心功能需求",
                "## 非功能需求",
                "## 需求优先级",
                "## 验收标准",
                "## 数据字典"
        };
        private boolean checkMermaid = true;
        private int minDataDictRows = 5;
    }

    /**
     * 架构师智能体专属配置
     */
    @Data
    public static class ArchitectAgentConfig {
        private String[] requiredSections = {
                "## 系统架构总览",
                "## 技术栈选型",
                "## 模块划分与职责",
                "## 接口定义（RESTful）",
                "## 数据库设计（核心表）",
                "## 部署架构",
                "## 性能/安全设计"
        };
        private int minApiCount = 5;
        private int minDbTableCount = 3;
    }
}


package top.whyh.agentai.coordinator;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * 智能体定义枚举：声明每个智能体的唯一标识、名称和依赖关系
 * 依赖关系用于在运行时检查：若某智能体被禁用，依赖它的智能体也无法执行
 */
@Getter
public enum AgentDefinition {

    REQUIREMENT_ANALYSIS(
            "requirement_analysis",
            "需求分析智能体",
            List.of()  // 无依赖，是起点
    ),
    ARCHITECT_DESIGN(
            "architect_design",
            "架构设计智能体",
            List.of("requirement_analysis")  // 依赖需求分析结果
    ),
    BACKEND_SKELETON(
            "backend_skeleton",
            "后端骨架生成智能体",
            List.of("architect_design")
    ),
    BACKEND_CONTROLLER(
            "backend_controller",
            "后端接口生成智能体",
            List.of("backend_skeleton")
    ),
    BACKEND_DOMAIN(
            "backend_domain",
            "后端领域层生成智能体",
            List.of("backend_skeleton")
    ),
    FRONTEND_SKELETON(
            "frontend_skeleton",
            "前端骨架生成智能体",
            List.of("architect_design")
    ),
    FRONTEND_VIEWS(
            "frontend_views",
            "前端页面生成智能体",
            List.of("frontend_skeleton", "backend_controller")  // 需要后端接口才能对齐
    ),
    FRONTEND_API(
            "frontend_api",
            "前端API封装智能体",
            List.of("frontend_skeleton", "backend_controller")
    ),
    CODE_REVIEW(
            "code_review",
            "代码审查智能体",
            List.of("backend_domain", "backend_controller")  // 至少需要后端代码才能审查
    );

    private final String agentCode;
    private final String agentName;
    private final List<String> dependencies;  // 依赖的 agentCode 列表

    AgentDefinition(String agentCode, String agentName, List<String> dependencies) {
        this.agentCode = agentCode;
        this.agentName = agentName;
        this.dependencies = dependencies;
    }

    public static AgentDefinition ofCode(String code) {
        return Arrays.stream(values())
                .filter(a -> a.agentCode.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知智能体: " + code));
    }
}

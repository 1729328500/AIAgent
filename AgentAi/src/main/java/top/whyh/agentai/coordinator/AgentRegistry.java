package top.whyh.agentai.coordinator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import top.whyh.agentai.entity.Agent;
import top.whyh.agentai.mapper.AgentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 智能体注册表：从数据库读取启用状态，并提供依赖检查能力
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentRegistry {

    private final AgentMapper agentMapper;

    /**
     * 获取当前所有已启用的智能体 role 集合（role 字段对应 AgentDefinition.agentCode）
     */
    public Set<String> getEnabledAgentCodes() {
        List<Agent> enabled = agentMapper.selectList(
                new LambdaQueryWrapper<Agent>().eq(Agent::getStatus, "active")
        );
        return enabled.stream().map(Agent::getRole).collect(Collectors.toSet());
    }

    /**
     * 判断某个智能体是否可以执行：
     * 1. 自身必须是 enabled 状态
     * 2. 所有依赖的智能体也必须是 enabled 状态
     *
     * @return true=可执行，false=跳过
     */
    public boolean canExecute(AgentDefinition agent, Set<String> enabledCodes) {
        if (!enabledCodes.contains(agent.getAgentCode())) {
            log.info("智能体 [{}] 未启用，跳过执行", agent.getAgentName());
            return false;
        }
        for (String dep : agent.getDependencies()) {
            if (!enabledCodes.contains(dep)) {
                log.warn("智能体 [{}] 的依赖 [{}] 未启用，无法执行", agent.getAgentName(), dep);
                return false;
            }
        }
        return true;
    }

    /**
     * 检查核心智能体（需求分析、架构设计）是否启用，
     * 若未启用则整个流程无法启动
     */
    public void validateCoreAgentsEnabled(Set<String> enabledCodes) {
        if (!enabledCodes.contains(AgentDefinition.REQUIREMENT_ANALYSIS.getAgentCode())) {
            throw new IllegalStateException("需求分析智能体未启用，无法启动流程");
        }
        if (!enabledCodes.contains(AgentDefinition.ARCHITECT_DESIGN.getAgentCode())) {
            throw new IllegalStateException("架构设计智能体未启用，无法启动流程");
        }
    }
}

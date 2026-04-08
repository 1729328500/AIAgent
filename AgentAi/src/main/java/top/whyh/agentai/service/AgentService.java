package top.whyh.agentai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.whyh.agentai.entity.Agent;
import top.whyh.agentai.mapper.AgentMapper;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentService {

    private final AgentMapper agentMapper;

    public List<Agent> getAllAgents() {
        return agentMapper.selectList(null);
    }

    public Agent getAgentById(String id) {
        return agentMapper.selectById(id);
    }

    public void updateAgentStatus(String id, String status) {
        Agent agent = agentMapper.selectById(id);
        if (agent == null) {
            throw new IllegalArgumentException("智能体不存在");
        }

        agent.setStatus(status);
        agentMapper.updateById(agent);
    }

    public void updateAgent(String id, String name, String capabilities, Double efficiencyScore, Double successRate) {
        Agent agent = agentMapper.selectById(id);
        if (agent == null) {
            throw new IllegalArgumentException("智能体不存在");
        }

        if (name != null) {
            agent.setName(name);
        }
        if (capabilities != null) {
            agent.setCapabilities(capabilities);
        }
        if (efficiencyScore != null) {
            agent.setEfficiencyScore(BigDecimal.valueOf(efficiencyScore));
        }
        if (successRate != null) {
            agent.setSuccessRate(BigDecimal.valueOf(successRate));
        }

        agentMapper.updateById(agent);
    }

    public Page<Agent> getAgentsByPage(int pageNum, int pageSize, String status) {
        Page<Agent> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Agent> wrapper = new LambdaQueryWrapper<>();

        if (status != null && !status.isEmpty()) {
            wrapper.eq(Agent::getStatus, status);
        }

        return agentMapper.selectPage(page, wrapper);
    }
}

package top.whyh.agentai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.whyh.agentai.entity.Agent;
import top.whyh.agentai.service.AgentService;
import top.whyh.starter.common.result.Result;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentManagementController {

    private final AgentService agentService;

    @GetMapping
    public Result<List<Agent>> getAllAgents() {
        return Result.success(agentService.getAllAgents());
    }

    @GetMapping("/page")
    public Result<Page<Agent>> getAgentsByPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status) {
        return Result.success(agentService.getAgentsByPage(pageNum, pageSize, status));
    }

    @GetMapping("/{id}")
    public Result<Agent> getAgentById(@PathVariable String id) {
        return Result.success(agentService.getAgentById(id));
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateAgentStatus(@PathVariable String id, @RequestBody UpdateStatusRequest request) {
        agentService.updateAgentStatus(id, request.getStatus());
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> updateAgent(@PathVariable String id, @RequestBody UpdateAgentRequest request) {
        agentService.updateAgent(id, request.getName(), request.getCapabilities(),
                request.getEfficiencyScore(), request.getSuccessRate());
        return Result.success();
    }

    @Data
    public static class UpdateStatusRequest {
        private String status;
    }

    @Data
    public static class UpdateAgentRequest {
        private String name;
        private String capabilities;
        private Double efficiencyScore;
        private Double successRate;
    }
}

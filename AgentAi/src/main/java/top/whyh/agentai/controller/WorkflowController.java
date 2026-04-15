package top.whyh.agentai.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.whyh.agentai.entity.WorkflowInstance;
import top.whyh.agentai.entity.WorkflowStep;
import top.whyh.agentai.entity.Artifact;
import top.whyh.agentai.service.WorkflowService;
import top.whyh.agentai.utils.SecurityUtils;
import top.whyh.starter.common.result.Result;

import java.util.List;

@RestController
@RequestMapping("/api/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @GetMapping("/{id}")
    public Result<WorkflowInstance> getWorkflowById(@PathVariable String id) {
        return Result.success(workflowService.getWorkflowById(id));
    }

    @GetMapping("/page")
    public Result<Page<WorkflowInstance>> getWorkflowsByPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status) {
        String userId = SecurityUtils.isAuthenticated() ? SecurityUtils.getCurrentUserId() : null;
        return Result.success(workflowService.getWorkflowsByPage(pageNum, pageSize, userId, status));
    }

    @GetMapping("/{id}/steps")
    public Result<List<WorkflowStep>> getWorkflowSteps(@PathVariable String id) {
        return Result.success(workflowService.getWorkflowSteps(id));
    }

    @GetMapping("/{id}/artifacts")
    public Result<List<Artifact>> getWorkflowArtifacts(@PathVariable String id) {
        return Result.success(workflowService.getWorkflowArtifacts(id));
    }

    @PostMapping("/{id}/cancel")
    public Result<Void> cancelWorkflow(@PathVariable String id) {
        workflowService.cancelWorkflow(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteWorkflow(@PathVariable String id) {
        workflowService.deleteWorkflow(id);
        return Result.success();
    }
}

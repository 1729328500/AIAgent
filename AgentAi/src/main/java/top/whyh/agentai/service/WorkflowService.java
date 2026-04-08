package top.whyh.agentai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.whyh.agentai.entity.WorkflowInstance;
import top.whyh.agentai.entity.WorkflowStep;
import top.whyh.agentai.entity.Artifact;
import top.whyh.agentai.mapper.WorkflowInstanceMapper;
import top.whyh.agentai.mapper.WorkflowStepMapper;
import top.whyh.agentai.mapper.ArtifactMapper;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowInstanceMapper workflowInstanceMapper;
    private final WorkflowStepMapper workflowStepMapper;
    private final ArtifactMapper artifactMapper;

    public WorkflowInstance getWorkflowById(String id) {
        return workflowInstanceMapper.selectById(id);
    }

    public Page<WorkflowInstance> getWorkflowsByPage(int pageNum, int pageSize, String userId, String status) {
        Page<WorkflowInstance> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<WorkflowInstance> wrapper = new LambdaQueryWrapper<>();

        // Note: workflow_instance table doesn't have user_id column
        // userId parameter is ignored for now

        if (status != null && !status.isEmpty()) {
            wrapper.eq(WorkflowInstance::getStatus, status);
        }

        wrapper.orderByDesc(WorkflowInstance::getCreatedTime);
        return workflowInstanceMapper.selectPage(page, wrapper);
    }

    public List<WorkflowStep> getWorkflowSteps(String workflowInstanceId) {
        LambdaQueryWrapper<WorkflowStep> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowStep::getWorkflowId, workflowInstanceId);
        wrapper.orderByAsc(WorkflowStep::getCreatedTime);
        return workflowStepMapper.selectList(wrapper);
    }

    public List<Artifact> getWorkflowArtifacts(String workflowInstanceId) {
        // Get all steps for this workflow
        LambdaQueryWrapper<WorkflowStep> stepWrapper = new LambdaQueryWrapper<>();
        stepWrapper.eq(WorkflowStep::getWorkflowId, workflowInstanceId);
        List<WorkflowStep> steps = workflowStepMapper.selectList(stepWrapper);

        if (steps.isEmpty()) {
            return List.of();
        }

        // Since workflow_step doesn't have task_id, we need to query artifacts differently
        // For now, return empty list until we understand the relationship
        return List.of();
    }

    public void cancelWorkflow(String workflowInstanceId) {
        WorkflowInstance workflow = workflowInstanceMapper.selectById(workflowInstanceId);
        if (workflow == null) {
            throw new IllegalArgumentException("工作流实例不存在");
        }

        if ("completed".equals(workflow.getStatus()) || "failed".equals(workflow.getStatus())) {
            throw new IllegalStateException("工作流已结束，无法取消");
        }

        workflow.setStatus("cancelled");
        workflowInstanceMapper.updateById(workflow);
    }
}

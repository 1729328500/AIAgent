package top.whyh.agentai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.whyh.agentai.entity.Artifact;
import top.whyh.agentai.entity.WorkflowInstance;
import top.whyh.agentai.entity.WorkflowStep;
import top.whyh.agentai.mapper.ArtifactMapper;
import top.whyh.agentai.mapper.WorkflowInstanceMapper;
import top.whyh.agentai.mapper.WorkflowStepMapper;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowInstanceMapper workflowInstanceMapper;
    private final WorkflowStepMapper workflowStepMapper;
    private final ArtifactMapper artifactMapper;

    /** outputData 最大存储长度（LONGTEXT 实际可存 4GB，但此处限制合理值） */
    private static final int MAX_OUTPUT_LEN = 20000;

    // ─────────────── 读方法 ───────────────

    public WorkflowInstance getWorkflowById(String id) {
        return workflowInstanceMapper.selectById(id);
    }

    public Page<WorkflowInstance> getWorkflowsByPage(int pageNum, int pageSize, String userId, String status) {
        Page<WorkflowInstance> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<WorkflowInstance> wrapper = new LambdaQueryWrapper<>();
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
        return List.of(); // 暂未启用 Artifact 表
    }

    // ─────────────── 写方法 ───────────────

    /**
     * 在任务启动时创建 WorkflowInstance，返回其 id（workflowId）。
     * workflowName 初始为占位符，由 updateWorkflowName() 在执行完成后更新。
     */
    public String createWorkflow(String taskId, String workflowName) {
        WorkflowInstance wf = new WorkflowInstance();
        wf.setTaskId(taskId);
        wf.setWorkflowName(workflowName);
        wf.setWorkflowType("FULL_GENERATE");
        wf.setStatus("running");
        wf.setStartTime(LocalDateTime.now());
        wf.setCreatedTime(LocalDateTime.now());
        wf.setUpdatedTime(LocalDateTime.now());
        workflowInstanceMapper.insert(wf);
        log.info("创建工作流记录 | workflowId: {} | taskId: {}", wf.getId(), taskId);
        return wf.getId();
    }

    /**
     * 智能体步骤完成后，向 workflow_step 表写入一条记录。
     * outputData 超过 MAX_OUTPUT_LEN 时自动截断。
     */
    public void recordStep(String workflowId, String stepName, String description,
                           String outputData, long durationMs, String status) {
        if (workflowId == null) return;
        try {
            WorkflowStep step = new WorkflowStep();
            step.setWorkflowId(workflowId);
            step.setStepName(stepName);
            step.setStepDescription(description);
            String truncated = outputData != null && outputData.length() > MAX_OUTPUT_LEN
                    ? outputData.substring(0, MAX_OUTPUT_LEN) + "\n…（内容已截断）"
                    : outputData;
            step.setOutputData(truncated);
            step.setStatus(status);
            step.setDuration((int) durationMs);
            step.setStartTime(LocalDateTime.now().minusNanos(durationMs * 1_000_000L));
            step.setEndTime(LocalDateTime.now());
            step.setCreatedTime(LocalDateTime.now());
            step.setUpdatedTime(LocalDateTime.now());
            workflowStepMapper.insert(step);
        } catch (Exception e) {
            log.warn("记录工作流步骤失败（不影响主流程）| workflowId: {} | step: {} | err: {}", workflowId, stepName, e.getMessage());
        }
    }

    /** 更新 workflow_instance 当前正在执行的步骤名（供列表页实时展示） */
    public void updateCurrentStep(String workflowId, String currentStep) {
        if (workflowId == null) return;
        try {
            WorkflowInstance wf = workflowInstanceMapper.selectById(workflowId);
            if (wf != null) {
                wf.setCurrentStep(currentStep);
                wf.setUpdatedTime(LocalDateTime.now());
                workflowInstanceMapper.updateById(wf);
            }
        } catch (Exception e) {
            log.warn("更新工作流当前步骤失败 | workflowId: {} | err: {}", workflowId, e.getMessage());
        }
    }

    /** 执行完成后用实际系统名更新 workflowName */
    public void updateWorkflowName(String workflowId, String workflowName) {
        if (workflowId == null) return;
        try {
            WorkflowInstance wf = workflowInstanceMapper.selectById(workflowId);
            if (wf != null) {
                wf.setWorkflowName(workflowName);
                wf.setUpdatedTime(LocalDateTime.now());
                workflowInstanceMapper.updateById(wf);
            }
        } catch (Exception e) {
            log.warn("更新工作流名称失败 | workflowId: {} | err: {}", workflowId, e.getMessage());
        }
    }

    /**
     * 将工作流推入终态（completed / failed / cancelled）。
     */
    public void finishWorkflow(String workflowId, String status) {
        if (workflowId == null) return;
        try {
            WorkflowInstance wf = workflowInstanceMapper.selectById(workflowId);
            if (wf != null) {
                wf.setStatus(status);
                wf.setCurrentStep(null);
                wf.setEndTime(LocalDateTime.now());
                wf.setUpdatedTime(LocalDateTime.now());
                workflowInstanceMapper.updateById(wf);
                log.info("工作流结束 | workflowId: {} | status: {}", workflowId, status);
            }
        } catch (Exception e) {
            log.warn("更新工作流终态失败 | workflowId: {} | err: {}", workflowId, e.getMessage());
        }
    }

    // ─────────────── 取消（前端操作） ───────────────

    public void cancelWorkflow(String workflowInstanceId) {
        WorkflowInstance workflow = workflowInstanceMapper.selectById(workflowInstanceId);
        if (workflow == null) throw new IllegalArgumentException("工作流实例不存在");
        if ("completed".equals(workflow.getStatus()) || "failed".equals(workflow.getStatus())) {
            throw new IllegalStateException("工作流已结束，无法取消");
        }
        workflow.setStatus("cancelled");
        workflow.setEndTime(LocalDateTime.now());
        workflow.setUpdatedTime(LocalDateTime.now());
        workflowInstanceMapper.updateById(workflow);
    }
}

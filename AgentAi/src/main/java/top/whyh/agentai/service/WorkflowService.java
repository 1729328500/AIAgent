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
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /** outputData 最大存储长度（LONGTEXT 实际可存 4GB，但此处限制合理值） */
    private static final int MAX_OUTPUT_LEN = 20000;

    // ─────────────── 读方法 ───────────────

    public WorkflowInstance getWorkflowById(String id) {
        return workflowInstanceMapper.selectById(id);
    }

    public WorkflowInstance getWorkflowByTaskId(String taskId) {
        LambdaQueryWrapper<WorkflowInstance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowInstance::getTaskId, taskId)
               .last("LIMIT 1");
        return workflowInstanceMapper.selectOne(wrapper);
    }

    public Page<WorkflowInstance> getWorkflowsByPage(int pageNum, int pageSize, String userId, String status) {
        Page<WorkflowInstance> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<WorkflowInstance> wrapper = new LambdaQueryWrapper<>();
        // 只返回当前用户的工作流；未登录时 userId 为 null，返回空
        if (userId != null && !userId.isEmpty()) {
            wrapper.eq(WorkflowInstance::getUserId, userId);
        } else {
            wrapper.eq(WorkflowInstance::getUserId, ""); // 未认证：确保返回空
        }
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
        LambdaQueryWrapper<Artifact> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Artifact::getWorkflowId, workflowInstanceId);
        wrapper.orderByAsc(Artifact::getCreatedTime);
        return artifactMapper.selectList(wrapper);
    }

    /**
     * 将生成过程中的关键产物持久化到 artifact 表。
     */
    public void persistArtifacts(String taskId, String workflowId, String systemName,
                                 String prdContent, String archContent, String projectFilesJson) {
        if (workflowId == null) return;
        try {
            // 1. 保存 PRD
            saveArtifact(taskId, workflowId, "document", systemName + " - 需求分析文档 (PRD)", prdContent);
            // 2. 保存架构设计
            saveArtifact(taskId, workflowId, "document", systemName + " - 系统架构设计", archContent);
            // 3. 保存完整项目代码 (JSON 格式)
            saveArtifact(taskId, workflowId, "code", systemName + " - 完整项目源码", projectFilesJson);
        } catch (Exception e) {
            log.warn("持久化产物失败 | workflowId: {} | err: {}", workflowId, e.getMessage());
        }
    }

    private void saveArtifact(String taskId, String workflowId, String type, String name, String content) {
        if (content == null) return;
        Artifact artifact = new Artifact();
        artifact.setTaskId(taskId);
        artifact.setWorkflowId(workflowId);
        artifact.setArtifactType(type);
        artifact.setName(name);
        artifact.setContent(content);
        artifact.setFileSize((long) content.length());
        artifact.setStatus("valid");
        artifact.setCreatedTime(LocalDateTime.now());
        artifact.setUpdatedTime(LocalDateTime.now());
        artifactMapper.insert(artifact);
    }

    // ─────────────── 写方法 ───────────────

    /**
     * 在任务启动时创建 WorkflowInstance，返回其 id（workflowId）。
     * workflowName 初始为占位符，由 updateWorkflowName() 在执行完成后更新。
     */
    public String createWorkflow(String taskId, String workflowName, String userId) {
        WorkflowInstance wf = new WorkflowInstance();
        wf.setTaskId(taskId);
        wf.setUserId(userId);
        wf.setWorkflowName(workflowName);
        wf.setWorkflowType("FULL_GENERATE");
        wf.setStatus("running");
        wf.setStartTime(LocalDateTime.now());
        wf.setCreatedTime(LocalDateTime.now());
        wf.setUpdatedTime(LocalDateTime.now());
        workflowInstanceMapper.insert(wf);
        log.info("创建工作流记录 | workflowId: {} | taskId: {} | userId: {}", wf.getId(), taskId, userId);
        return wf.getId();
    }

    /**
     * 智能体步骤完成后，向 workflow_step 表写入一条记录。
     * outputData 超过 MAX_OUTPUT_LEN 时自动截断。
     */
    public void recordStep(String workflowId, String agentId, String stepName, String description,
                           String outputData, long durationMs, String status) {
        if (workflowId == null) return;
        try {
            WorkflowStep step = new WorkflowStep();
            step.setWorkflowId(workflowId);
            step.setAgentId(agentId);
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

    /** 过载方法，兼容旧调用 */
    public void recordStep(String workflowId, String stepName, String description,
                           String outputData, long durationMs, String status) {
        recordStep(workflowId, null, stepName, description, outputData, durationMs, status);
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

    /** 保存全流程生成的最终结果到数据库 */
    public void saveResult(String workflowId, Object result) {
        if (workflowId == null || result == null) return;
        try {
            WorkflowInstance wf = workflowInstanceMapper.selectById(workflowId);
            if (wf != null) {
                wf.setResultJson(objectMapper.writeValueAsString(result));
                wf.setUpdatedTime(LocalDateTime.now());
                workflowInstanceMapper.updateById(wf);
            }
        } catch (Exception e) {
            log.warn("保存工作流结果失败 | workflowId: {} | err: {}", workflowId, e.getMessage());
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

    /** 更新工作流的沙箱部署状态 */
    public void updateSandboxStatus(String workflowId, String status, String sandboxId, String sandboxUrl, String sandboxError) {
        if (workflowId == null) return;
        try {
            WorkflowInstance wf = workflowInstanceMapper.selectById(workflowId);
            if (wf != null) {
                wf.setSandboxStatus(status);
                wf.setSandboxId(sandboxId);
                wf.setSandboxUrl(sandboxUrl);
                wf.setSandboxError(sandboxError);
                wf.setUpdatedTime(LocalDateTime.now());
                workflowInstanceMapper.updateById(wf);
            }
        } catch (Exception e) {
            log.warn("更新沙箱状态失败 | workflowId: {} | err: {}", workflowId, e.getMessage());
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

    /**
     * 删除工作流记录及关联的所有步骤和产物。
     */
    public void deleteWorkflow(String workflowId) {
        WorkflowInstance workflow = workflowInstanceMapper.selectById(workflowId);
        if (workflow == null) return;

        // 1. 删除关联的步骤
        workflowStepMapper.delete(new LambdaQueryWrapper<WorkflowStep>()
                .eq(WorkflowStep::getWorkflowId, workflowId));

        // 2. 删除关联的产物
        artifactMapper.delete(new LambdaQueryWrapper<Artifact>()
                .eq(Artifact::getWorkflowId, workflowId));

        // 3. 删除工作流实例本身
        workflowInstanceMapper.deleteById(workflowId);
        
        log.info("已删除工作流记录及其关联数据 | workflowId: {}", workflowId);
    }
}

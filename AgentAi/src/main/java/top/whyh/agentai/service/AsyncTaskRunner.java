package top.whyh.agentai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import top.whyh.agentai.cache.RedisCache;
import top.whyh.agentai.coordinator.AgentOrchestrator;
import top.whyh.agentai.model.GenerationTask;
import top.whyh.agentai.result.SystemGenerateResult;

import java.util.concurrent.TimeUnit;

/**
 * 异步任务执行器（独立 Bean，避免 Spring 自调用导致 @Async 失效）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncTaskRunner {

    private final RedisCache redisCache;
    private final AgentOrchestrator agentOrchestrator;
    private final WorkflowService workflowService;

    private static final String TASK_KEY_PREFIX = "agent:task:";
    private static final long TASK_TTL_HOURS = 24;

    @Async
    public void run(String taskId, String userInput, String userId) {
        // 1. 立即创建 WorkflowInstance（带 userId），前端可通过 workflowId 跳转详情
        String workflowId = workflowService.createWorkflow(taskId, "处理中...", userId);

        // 2. 把 workflowId 写回 Redis，SSE 推送时前端即可读到
        GenerationTask task = getTask(taskId);
        if (task != null) {
            task.setWorkflowId(workflowId);
            saveTask(taskId, task);
        }

        try {
            SystemGenerateResult result = agentOrchestrator.executeFullFlow(
                    userInput,
                    progress -> updateTaskStatus(taskId, "running", progress),
                    () -> isCancelled(taskId),
                    workflowId
            );

            // 若流程自行检测到取消标志并返回 cancelled 结果，更新任务状态
            if ("cancelled".equals(result.getStatus())) {
                log.info("任务已取消 | taskId: {}", taskId);
                updateTaskStatus(taskId, "cancelled", "任务已被用户取消");
                workflowService.finishWorkflow(workflowId, "cancelled");
                return;
            }

            // 正常完成：仅在任务仍为 running 时更新（防止并发下覆盖 cancelled）
            GenerationTask currentTask = getTask(taskId);
            if (currentTask != null && !"cancelled".equals(currentTask.getStatus())) {
                currentTask.setStatus("success");
                currentTask.setResult(result);
                currentTask.setMessage(result.getErrorMsg() != null && !result.getErrorMsg().isEmpty()
                        ? result.getErrorMsg()
                        : "项目生成完成，请在预览页面确认后保存");
                saveTask(taskId, currentTask);

                // 更新工作流名称为真实系统名
                workflowService.updateWorkflowName(workflowId, result.getSystemName());
                workflowService.finishWorkflow(workflowId, "completed");
            }

        } catch (Exception e) {
            log.error("异步任务执行失败 | taskId: {} | 错误: {}", taskId, e.getMessage(), e);
            GenerationTask currentTask = getTask(taskId);
            if (currentTask != null && !"cancelled".equals(currentTask.getStatus())) {
                updateTaskStatus(taskId, "failed", "执行失败: " + e.getMessage());
            }
            workflowService.finishWorkflow(workflowId, "failed");
        }
    }

    private boolean isCancelled(String taskId) {
        GenerationTask task = getTask(taskId);
        return task != null && "cancelled".equals(task.getStatus());
    }

    private GenerationTask getTask(String taskId) {
        return redisCache.get(TASK_KEY_PREFIX + taskId, GenerationTask.class);
    }

    private void saveTask(String taskId, GenerationTask task) {
        redisCache.set(TASK_KEY_PREFIX + taskId, task, TASK_TTL_HOURS, TimeUnit.HOURS);
    }

    private void updateTaskStatus(String taskId, String status, String message) {
        GenerationTask task = getTask(taskId);
        if (task != null) {
            task.setStatus(status);
            task.setMessage(message);
            saveTask(taskId, task);
        }
    }
}

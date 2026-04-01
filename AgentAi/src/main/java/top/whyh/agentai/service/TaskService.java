// top/whyh/agentai/service/TaskService.java
package top.whyh.agentai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import top.whyh.agentai.cache.RedisCache;
import top.whyh.agentai.coordinator.AgentOrchestrator;
import top.whyh.agentai.model.GenerationTask;
import top.whyh.agentai.result.SystemGenerateResult;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final RedisCache redisCache;
    private final AgentOrchestrator agentOrchestrator;

    private static final String TASK_KEY_PREFIX = "agent:task:";
    private static final long TASK_TTL_HOURS = 24;

    public String submitTask(String userInput) {
        String taskId = "task_" + UUID.randomUUID().toString().replace("-", "");
        GenerationTask task = new GenerationTask();
        task.setTaskId(taskId);
        task.setStatus("pending");
        task.setMessage("任务已提交，等待处理");
        saveTask(taskId, task);
        // 异步启动执行
        executeTaskAsync(taskId, userInput);
        return taskId;
    }

    @Async // 必须启用 @EnableAsync
    public void executeTaskAsync(String taskId, String userInput) {
        try {
            SystemGenerateResult result = agentOrchestrator.executeFullFlow(
                    userInput,
                    progress -> updateTaskStatus(taskId, "running", progress)
            );
            GenerationTask task = getTask(taskId);
            if (task != null) {
                task.setStatus("success");
                task.setResult(result);
                task.setMessage("项目生成成功");
                saveTask(taskId, task);
            }
        } catch (Exception e) {
            updateTaskStatus(taskId, "failed", "执行失败: " + e.getMessage());
        }
    }

    public GenerationTask getTask(String taskId) {
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
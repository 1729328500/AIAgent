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
    private final CodeOutputService codeOutputService;

    private static final String TASK_KEY_PREFIX = "agent:task:";
    private static final long TASK_TTL_HOURS = 24;

    public String submitTask(String userInput) {
        String taskId = "task_" + UUID.randomUUID().toString().replace("-", "");
        GenerationTask task = new GenerationTask();
        task.setTaskId(taskId);
        task.setStatus("pending");
        task.setMessage("任务已提交，等待处理");
        saveTask(taskId, task);
        executeTaskAsync(taskId, userInput);
        return taskId;
    }

    @Async
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
                task.setMessage("项目生成完成，请在预览页面确认后保存");
                saveTask(taskId, task);
            }
        } catch (Exception e) {
            updateTaskStatus(taskId, "failed", "执行失败: " + e.getMessage());
        }
    }

    /**
     * 用户确认后，将 Redis 中的 projectFiles 落盘保存
     */
    public String saveTaskProject(String taskId) {
        GenerationTask task = getTask(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在或已过期");
        }
        if (!"success".equals(task.getStatus())) {
            throw new IllegalArgumentException("任务尚未完成，无法保存");
        }
        SystemGenerateResult result = task.getResult();
        if (result == null || result.getProjectFiles() == null || result.getProjectFiles().isEmpty()) {
            throw new IllegalArgumentException("任务没有可保存的项目文件");
        }
        if (result.getSavedProjectPath() != null) {
            return result.getSavedProjectPath();
        }
        String savedPath = codeOutputService.saveGeneratedProject(result.getSystemName(), result.getProjectFiles());
        result.setSavedProjectPath(savedPath);
        saveTask(taskId, task);
        return savedPath;
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

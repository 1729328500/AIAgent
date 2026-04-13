// top/whyh/agentai/service/TaskService.java
package top.whyh.agentai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import top.whyh.agentai.cache.RedisCache;
import top.whyh.agentai.model.GenerationTask;
import top.whyh.agentai.result.SystemGenerateResult;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final RedisCache redisCache;
    private final AsyncTaskRunner asyncTaskRunner;
    private final CodeOutputService codeOutputService;
    private final RequirementAnalysisService requirementAnalysisService;
    private final ArchitectAnalysisService architectAnalysisService;

    private static final String TASK_KEY_PREFIX = "agent:task:";
    private static final long TASK_TTL_HOURS = 24;

    public String submitTask(String userInput) {
        String taskId = "task_" + UUID.randomUUID().toString().replace("-", "");
        GenerationTask task = new GenerationTask();
        task.setTaskId(taskId);
        task.setStatus("pending");
        task.setMessage("任务已提交，等待处理");
        saveTask(taskId, task);
        // 调用独立 Bean 的 @Async 方法，避免 Spring 自调用导致 @Async 失效
        asyncTaskRunner.run(taskId, userInput);
        return taskId;
    }

    /**
     * 用户确认后，将 Redis 中的产物全部落盘保存（代码 + PRD 文档 + 架构文档）
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

        // ── 保存代码 ──
        if (result.getSavedProjectPath() == null) {
            String savedPath = codeOutputService.saveGeneratedProject(result.getSystemName(), result.getProjectFiles());
            result.setSavedProjectPath(savedPath);
        }

        // ── 保存 PRD 文档 ──
        if (result.getPrdContent() != null && result.getSavedPrdPath() == null) {
            try {
                String prdPath = requirementAnalysisService.saveDocument(
                        result.getSystemName(), result.getPrdDocumentId(), result.getPrdContent());
                result.setSavedPrdPath(prdPath);
            } catch (Exception e) {
                // 文档保存失败不阻断整体流程
            }
        }

        // ── 保存架构文档 ──
        if (result.getArchContent() != null && result.getSavedArchPath() == null) {
            try {
                String archPath = architectAnalysisService.saveDocument(
                        result.getSystemName(), result.getArchDocumentId(), result.getArchContent());
                result.setSavedArchPath(archPath);
            } catch (Exception e) {
                // 文档保存失败不阻断整体流程
            }
        }

        saveTask(taskId, task);
        return result.getSavedProjectPath();
    }

    public void cancelTask(String taskId) {
        GenerationTask task = getTask(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在或已过期");
        }
        if (!"pending".equals(task.getStatus()) && !"running".equals(task.getStatus())) {
            throw new IllegalArgumentException("只有进行中的任务才能取消（当前状态：" + task.getStatus() + "）");
        }
        task.setStatus("cancelled");
        task.setMessage("任务已被用户取消，正在停止...");
        saveTask(taskId, task);
    }

    public GenerationTask getTask(String taskId) {
        return redisCache.get(TASK_KEY_PREFIX + taskId, GenerationTask.class);
    }

    private void saveTask(String taskId, GenerationTask task) {
        redisCache.set(TASK_KEY_PREFIX + taskId, task, TASK_TTL_HOURS, TimeUnit.HOURS);
    }

}

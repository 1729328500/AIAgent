package top.whyh.agentai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import top.whyh.agentai.cache.RedisCache;
import top.whyh.agentai.exception.ServerException;
import top.whyh.agentai.model.GenerationTask;
import top.whyh.agentai.result.SandboxResult;
import top.whyh.agentai.service.SandboxDeploymentService;
import top.whyh.agentai.service.TaskService;
import top.whyh.starter.common.result.Result;

import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final TaskService taskService;
    private final ObjectMapper objectMapper;
    private final SandboxDeploymentService sandboxDeploymentService;
    private final RedisCache redisCache;

    private static final String TASK_KEY_PREFIX = "agent:task:";
    private static final long TASK_TTL_HOURS = 24;

    @PostMapping("/generate")
    public Result<?> generateProject(@RequestBody GenerateRequest request) {
        String taskId = taskService.submitTask(request.getUserInput());
        return Result.success(new TaskSubmitResponse(taskId, "任务已提交，正在处理中"));
    }

    @GetMapping("/task/{taskId}")
    public Result<?> getTaskResult(@PathVariable String taskId) {
        GenerationTask task = taskService.getTask(taskId);
        if (task == null) {
            return Result.success(null);
        }
        return Result.success(task);
    }

    /**
     * SSE 任务进度推送端点——取代前端轮询。
     * 服务端每 3 秒从 Redis 读取任务状态并推送 "update" 事件；
     * 任务进入终态（success/failed/cancelled）后自动关闭连接。
     */
    @GetMapping(value = "/task/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTaskResult(@PathVariable String taskId) {
        SseEmitter emitter = new SseEmitter(35 * 60 * 1000L);

        Thread thread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    GenerationTask task = taskService.getTask(taskId);
                    if (task == null) {
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data("{\"status\":\"not_found\",\"message\":\"任务不存在或已过期\"}"));
                        emitter.complete();
                        return;
                    }
                    emitter.send(SseEmitter.event()
                            .name("update")
                            .data(objectMapper.writeValueAsString(task)));

                    String status = task.getStatus();
                    if ("success".equals(status) || "failed".equals(status) || "cancelled".equals(status)) {
                        emitter.complete();
                        return;
                    }
                    Thread.sleep(3000);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("SSE stream error | taskId: {} | {}", taskId, e.getMessage());
                emitter.completeWithError(e);
            }
        });
        thread.setDaemon(true);
        thread.setName("sse-" + taskId.substring(Math.max(0, taskId.length() - 8)));

        emitter.onTimeout(thread::interrupt);
        emitter.onCompletion(thread::interrupt);
        emitter.onError(t -> thread.interrupt());

        thread.start();
        return emitter;
    }

    @PostMapping("/task/{taskId}/save")
    public Result<?> saveProject(@PathVariable String taskId) {
        String savedPath = taskService.saveTaskProject(taskId);
        return Result.success(new SaveResponse(savedPath, "项目已成功保存到本地"));
    }

    @PostMapping("/task/{taskId}/cancel")
    public Result<?> cancelTask(@PathVariable String taskId) {
        taskService.cancelTask(taskId);
        return Result.success("取消请求已发送，任务将在当前步骤完成后停止");
    }

    /**
     * 将生成的前端项目部署到 E2B 沙箱进行预览。
     * 为控制成本，仅部署 frontend/ 目录，沙箱 30 分钟后自动销毁。
     */
    @PostMapping("/task/{taskId}/deploy")
    public Result<?> deployToSandbox(@PathVariable String taskId) {
        GenerationTask task = taskService.getTask(taskId);
        if (task == null) {
            throw new ServerException("任务不存在或已过期");
        }
        if (!"success".equals(task.getStatus()) || task.getResult() == null) {
            throw new ServerException("任务尚未完成，无法部署");
        }
        if (task.getResult().getProjectFiles() == null || task.getResult().getProjectFiles().isEmpty()) {
            throw new ServerException("项目文件为空，无法部署");
        }

        // 防止重复部署：已有运行中的沙箱则直接返回
        if ("running".equals(task.getSandboxStatus()) && task.getSandboxUrl() != null) {
            return Result.success(new DeployResponse(task.getSandboxId(), task.getSandboxUrl(),
                    "沙箱已在运行中，无需重复部署"));
        }

        // 更新状态为部署中
        task.setSandboxStatus("deploying");
        task.setSandboxError(null);
        saveTask(taskId, task);

        log.info("开始部署任务 [{}] 到 E2B 沙箱 | 系统: {}", taskId, task.getResult().getSystemName());

        SandboxResult result = sandboxDeploymentService.deployFrontend(
                task.getResult().getSystemName(),
                task.getResult().getProjectFiles()
        );

        if (result.isSuccess()) {
            task.setSandboxId(result.getSandboxId());
            task.setSandboxUrl(result.getPreviewUrl());
            task.setSandboxStatus("running");
            task.setSandboxError(null);
            saveTask(taskId, task);
            log.info("部署成功 | taskId: {} | url: {}", taskId, result.getPreviewUrl());
            return Result.success(new DeployResponse(result.getSandboxId(), result.getPreviewUrl(),
                    "部署成功！npm install 正在后台运行，请等待约 1-2 分钟后访问预览链接"));
        } else {
            task.setSandboxStatus("failed");
            task.setSandboxError(result.getErrorMessage());
            saveTask(taskId, task);
            log.error("部署失败 | taskId: {} | 错误: {}", taskId, result.getErrorMessage());
            throw new ServerException("沙箱部署失败: " + result.getErrorMessage());
        }
    }

    private void saveTask(String taskId, GenerationTask task) {
        redisCache.set(TASK_KEY_PREFIX + taskId, task, TASK_TTL_HOURS, TimeUnit.HOURS);
    }

    // ── Inner DTOs ─────────────────────────────────────────────────────────────

    @Data
    public static class GenerateRequest {
        private String userInput;
    }

    @Data
    public static class TaskSubmitResponse {
        private final String taskId;
        private final String message;
    }

    @Data
    public static class SaveResponse {
        private final String savedPath;
        private final String message;
    }

    @Data
    public static class DeployResponse {
        private final String sandboxId;
        private final String previewUrl;
        private final String message;
    }
}

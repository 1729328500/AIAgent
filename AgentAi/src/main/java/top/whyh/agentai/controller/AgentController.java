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
import top.whyh.agentai.service.WorkflowService;
import top.whyh.agentai.utils.SecurityUtils;
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
    private final WorkflowService workflowService;

    private static final String TASK_KEY_PREFIX = "agent:task:";
    private static final long TASK_TTL_HOURS = 24;

    @PostMapping("/generate")
    public Result<?> generateProject(@RequestBody GenerateRequest request) {
        // 在请求线程中获取用户ID（SecurityContext 仅在请求线程的 ThreadLocal 中有效）
        String userId = SecurityUtils.isAuthenticated() ? SecurityUtils.getCurrentUserId() : null;
        String taskId = taskService.submitTask(request.getUserInput(), userId);
        return Result.success(new TaskSubmitResponse(taskId, "任务已提交，正在处理中"));
    }

    @GetMapping("/task/{taskId}")
    public Result<?> getTaskResult(@PathVariable String taskId) {
        GenerationTask task = taskService.getTask(taskId);
        if (task == null) {
            return Result.success(null);
        }
        // 校验权限：仅允许本人查询（匿名用户不可查询有归属的任务）
        String currentUserId = SecurityUtils.isAuthenticated() ? SecurityUtils.getCurrentUserId() : null;
        if (task.getUserId() != null && !task.getUserId().equals(currentUserId)) {
            return Result.error("您没有权限查看此任务");
        }
        return Result.success(task);
    }

    /**
     * SSE 任务进度推送端点。
     *
     * <p>关键修复：SecurityContext 基于 ThreadLocal，后台线程不继承请求线程的上下文，
     * 因此必须在 Controller 方法（请求线程）中提前捕获 userId，通过 final 变量传入线程闭包。
     *
     * <p>前端使用 fetch + Authorization 头调用本端点，而非 EventSource（EventSource 不支持自定义头）。
     */
    @GetMapping(value = "/task/{taskId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTaskResult(@PathVariable String taskId) {
        // ★ 在请求线程中捕获，SecurityContext 此时有效
        final String currentUserId = SecurityUtils.isAuthenticated() ? SecurityUtils.getCurrentUserId() : null;

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
                    // 权限校验：任务有归属且请求者与归属不符 → 拒绝
                    if (task.getUserId() != null && !task.getUserId().equals(currentUserId)) {
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data("{\"status\":\"forbidden\",\"message\":\"您没有权限订阅此任务\"}"));
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
    @PostMapping("/task/{taskId}/kill-sandbox")
    public Result<?> killSandbox(@PathVariable String taskId) {
        GenerationTask task = taskService.getTask(taskId);
        if (task == null) throw new ServerException("任务不存在或已过期");
        if (task.getSandboxId() == null) throw new ServerException("该任务没有运行中的沙箱");

        // 调用代理服务关闭沙箱
        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            java.util.Map<String, String> body = new java.util.HashMap<>();
            body.put("sandboxId", task.getSandboxId());
            new org.springframework.web.client.RestTemplate().postForEntity(
                    sandboxDeploymentService.getProxyUrl() + "/kill-sandbox",
                    new org.springframework.http.HttpEntity<>(body, headers),
                    java.util.Map.class
            );
        } catch (Exception e) {
            log.warn("关闭沙箱请求失败（沙箱可能已自动销毁）| sandboxId: {} | err: {}", task.getSandboxId(), e.getMessage());
        }

        // 无论代理是否成功，都清除本地状态
        task.setSandboxStatus("none");
        task.setSandboxId(null);
        task.setSandboxUrl(null);
        task.setSandboxError(null);
        saveTask(taskId, task);
        workflowService.updateSandboxStatus(task.getWorkflowId(), "none", null, null, null);

        return Result.success("沙箱已关闭");
    }

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

        // 防止重复部署：Redis 或 DB 中已有运行中的沙箱则直接返回
        String workflowId = task.getWorkflowId();
        if ("running".equals(task.getSandboxStatus()) && task.getSandboxUrl() != null) {
            return Result.success(new DeployResponse(task.getSandboxId(), task.getSandboxUrl(),
                    "沙箱已在运行中，无需重复部署"));
        }
        // 历史任务从 DB 恢复时 Redis 中无沙箱状态，补充从 DB 读取
        if (workflowId != null && task.getSandboxStatus() == null) {
            top.whyh.agentai.entity.WorkflowInstance wf = workflowService.getWorkflowById(workflowId);
            if (wf != null && "running".equals(wf.getSandboxStatus()) && wf.getSandboxUrl() != null) {
                return Result.success(new DeployResponse(wf.getSandboxId(), wf.getSandboxUrl(),
                        "沙箱已在运行中，无需重复部署"));
            }
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
            // 同步持久化到 DB，历史任务重新部署后状态不丢失
            workflowService.updateSandboxStatus(workflowId, "running",
                    result.getSandboxId(), result.getPreviewUrl(), null);
            log.info("部署成功 | taskId: {} | url: {}", taskId, result.getPreviewUrl());
            return Result.success(new DeployResponse(result.getSandboxId(), result.getPreviewUrl(), "部署成功！"));
        } else {
            task.setSandboxStatus("failed");
            task.setSandboxError(result.getErrorMessage());
            saveTask(taskId, task);
            workflowService.updateSandboxStatus(workflowId, "failed", null, null, result.getErrorMessage());
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

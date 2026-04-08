package top.whyh.agentai.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import top.whyh.agentai.model.GenerationTask;
import top.whyh.agentai.service.TaskService;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final TaskService taskService;

    @PostMapping("/generate")
    public ResponseEntity<?> generateProject(@RequestBody GenerateRequest request) {
        String taskId = taskService.submitTask(request.getUserInput());
        return ResponseEntity.accepted()
                .body(new TaskSubmitResponse(taskId, "任务已提交，请通过 GET /api/agent/task/" + taskId + " 查询结果"));
    }

    @GetMapping("/task/{taskId}")
    public ResponseEntity<?> getTaskResult(@PathVariable String taskId) {
        GenerationTask task = taskService.getTask(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(task);
    }

    /**
     * 用户在前端预览产物后，确认保存到本地
     */
    @PostMapping("/task/{taskId}/save")
    public ResponseEntity<?> saveProject(@PathVariable String taskId) {
        try {
            String savedPath = taskService.saveTaskProject(taskId);
            return ResponseEntity.ok(new SaveResponse(savedPath, "项目已成功保存到本地"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ErrorResponse("保存失败: " + e.getMessage()));
        }
    }

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
    public static class ErrorResponse {
        private final String error;
    }
}

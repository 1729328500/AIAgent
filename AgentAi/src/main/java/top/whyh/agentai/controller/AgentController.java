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

    @Data
    public static class GenerateRequest {
        private String userInput;
    }

    @Data
    public static class TaskSubmitResponse {
        private final String taskId;
        private final String message;
    }
}

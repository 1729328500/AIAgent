package top.whyh.agentai.controller;

import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.whyh.agentai.coordinator.AgentOrchestrator;
import top.whyh.agentai.result.SystemGenerateResult;

import javax.validation.Valid;

/**
 * 系统生成统一入口Controller（接收"为我编写一个XX系统"请求）
 */
@RestController
@RequestMapping("/api/system")
public class SystemGenerateController {
    private final AgentOrchestrator agentOrchestrator;

    public SystemGenerateController(AgentOrchestrator agentOrchestrator) {
        this.agentOrchestrator = agentOrchestrator;
    }

    /**
     * 生成完整系统文档（PRD+架构文档+接口定义）
     */
    @PostMapping("/generate")
    public ResponseEntity<SystemGenerateResult> generateSystem(
            @Valid @RequestBody SystemGenerateRequest request) {
        SystemGenerateResult result = agentOrchestrator.executeFullFlow(request.getUserInput());
        return ResponseEntity.ok(result);
    }

    /**
     * 请求参数类
     */
    @Data
    public static class SystemGenerateRequest {
        @NotBlank(message = "请输入系统生成请求（示例：为我编写一个电商订单系统）")
        private String userInput;
    }

    /**
     * 全局异常处理
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<SystemGenerateResult> handleException(Exception e) {
        SystemGenerateResult errorResult = new SystemGenerateResult(
                "",             // requestId
                "",             // systemName
                "",             // prdDocumentId
                "",             // prdStoragePath
                "",             // archDocumentId
                "",             // archStoragePath
                null,           // prdContent
                null,           // archContent
                null,           // projectFiles
                null,           // workflowId
                "fail",         // status
                e.getMessage(), // errorMsg
                0L              // totalCostMs
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
    }
}
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
        // 严格匹配构造函数参数：String(String,String,String,String,String,String,String,String,long)
        SystemGenerateResult errorResult = new SystemGenerateResult(
                "",          // 1. requestId：请求ID（异常场景无请求ID则填空）
                "agentai",   // 2. systemName：系统名称（填实际系统名，如agentai）
                "",          // 3. prdDocumentId：PRD文档ID（异常场景无则填空）
                "",          // 4. prdStoragePath：PRD存储路径（异常场景无则填空）
                "",          // 5. archDocumentId：架构文档ID（异常场景无则填空）
                "",          // 6. archStoragePath：架构存储路径（异常场景无则填空）
                "fail",      // 7. status：执行状态（固定fail）
                e.getMessage(), // 8. errorMsg：错误信息（异常详情）
                "",
                0L          // 9. totalCostMs：总耗时（long类型，异常场景耗时为0）
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
    }
}
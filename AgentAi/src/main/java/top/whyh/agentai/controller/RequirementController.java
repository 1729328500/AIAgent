package top.whyh.agentai.controller;

import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.whyh.agentai.dto.RequirementRequest;
import top.whyh.agentai.dto.RequirementResponse;
import top.whyh.agentai.result.RequirementResult;
import top.whyh.agentai.service.RequirementAnalysisService;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/requirement")
public class RequirementController {

    private final RequirementAnalysisService analysisService;

    public RequirementController(RequirementAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/generate")
    public ResponseEntity<RequirementResponse> generateRequirementDocument(
            @Valid @RequestBody RequirementRequest request) {

        RequirementResult result =
                null;
        try {
            result = analysisService.generateRequirementDocument(request.getRequirementDescription());
        } catch (GraphRunnerException e) {
            throw new RuntimeException(e);
        }

        RequirementResponse response = new RequirementResponse();
        response.setDocumentId(result.getDocumentId());
        response.setDocumentContent(result.getDocumentContent());
        response.setStoragePath(result.getStoragePath());

        return ResponseEntity.ok(response);
    }

    // 全局异常处理（统一返回状态码，避免抛RuntimeException）
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("参数错误：" + e.getMessage());
    }

    @ExceptionHandler(GraphRunnerException.class)
    public ResponseEntity<String> handleGraphRunnerException(GraphRunnerException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("AI生成文档失败：" + e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGeneralException(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("文档生成失败：" + e.getMessage());
    }
}
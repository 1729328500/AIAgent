package top.whyh.agentai.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import top.whyh.agentai.service.CodeGenerationService;

@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
@Slf4j
public class TestAnythingLLMController {

    private final CodeGenerationService codeGenerationService;

    /**
     * 测试接口：手动触发代码生成
     *
     * 示例请求：
     * POST /test/generate
     * {
     *   "systemName": "用户管理",
     *   "prdContent": "支持用户注册、登录、查看个人信息。",
     *   "archContent": "使用 Spring Boot 3，MySQL，RESTful API。"
     * }
     */
    @PostMapping("/generate")
    public String testGenerateCode(@RequestBody TestRequest request) {
        try {
            String code = codeGenerationService.generateCode(
                    request.getSystemName(),
                    request.getPrdContent(),
                    request.getArchContent()
            );
            return code;
        } catch (Exception e) {
            log.error("测试代码生成失败", e);
            return "❌ 错误: " + e.getMessage();
        }
    }

    public static class TestRequest {
        private String systemName;
        private String prdContent;
        private String archContent;

        // Getters and Setters
        public String getSystemName() { return systemName; }
        public void setSystemName(String systemName) { this.systemName = systemName; }
        public String getPrdContent() { return prdContent; }
        public void setPrdContent(String prdContent) { this.prdContent = prdContent; }
        public String getArchContent() { return archContent; }
        public void setArchContent(String archContent) { this.archContent = archContent; }
    }
}
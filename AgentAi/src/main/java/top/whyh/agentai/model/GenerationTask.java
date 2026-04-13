// top/whyh/agentai/model/GenerationTask.java
package top.whyh.agentai.model;

import lombok.Data;
import top.whyh.agentai.result.SystemGenerateResult;

@Data
public class GenerationTask {
    private String taskId;
    private String status; // "pending", "running", "success", "failed"
    private String message; // 进度描述或错误信息
    private SystemGenerateResult result; // 成功时填充
    /** 关联的 workflow_instance.id，任务启动后立即写入，前端可用于跳转工作流详情 */
    private String workflowId;
    private long createTime = System.currentTimeMillis();

    // ── E2B 沙箱字段 ──────────────────────────────────────────────────────────
    /** E2B 沙箱 ID */
    private String sandboxId;
    /** 沙箱前端预览 URL（如 https://5173-xxx.e2b.dev） */
    private String sandboxUrl;
    /** 沙箱部署状态：none / deploying / running / failed */
    private String sandboxStatus = "none";
    /** 沙箱错误信息（deploying / failed 时填充） */
    private String sandboxError;
}
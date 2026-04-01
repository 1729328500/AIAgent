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
    private long createTime = System.currentTimeMillis();
}
package top.whyh.agentai.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * E2B 沙箱部署结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SandboxResult {

    /** 部署是否成功 */
    private boolean success;

    /** E2B 沙箱 ID */
    private String sandboxId;

    /** 前端预览地址（如 https://5173-xxx.e2b.dev） */
    private String previewUrl;

    /** 失败时的错误信息 */
    private String errorMessage;

    /** 沙箱超时时间（秒），超时后自动销毁 */
    private int timeoutSeconds;

    public static SandboxResult success(String sandboxId, String previewUrl, int timeoutSeconds) {
        return SandboxResult.builder()
                .success(true)
                .sandboxId(sandboxId)
                .previewUrl(previewUrl)
                .timeoutSeconds(timeoutSeconds)
                .build();
    }

    public static SandboxResult failure(String errorMessage) {
        return SandboxResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}

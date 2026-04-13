package top.whyh.agentai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审查发现的具体问题
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewIssue {
    /** 问题类型：MISSING_FILE (文件缺失), LOGIC_ERROR (逻辑错误), INCONSISTENCY (前后端不一致) */
    private String type;
    /** 问题描述 */
    private String description;
    /** 涉及到的文件路径（如果有） */
    private String filePath;
    /**
     * 问题严重等级：
     * CRITICAL - 会导致项目无法编译或运行（必须自动修复）
     * WARNING  - 不影响运行，仅展示给用户参考
     */
    private String severity = "CRITICAL";

    public boolean isCritical() {
        return !"WARNING".equalsIgnoreCase(severity);
    }
}

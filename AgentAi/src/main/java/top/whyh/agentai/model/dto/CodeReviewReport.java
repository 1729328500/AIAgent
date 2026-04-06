package top.whyh.agentai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 代码审查报告 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeReviewReport {
    /** 问题列表 */
    private List<ReviewIssue> issues = new ArrayList<>();
    /** 缺失的文件路径列表 */
    private List<String> missingFiles = new ArrayList<>();
    /** 审查是否通过 */
    private boolean passed;
    /** 总体建议 */
    private String summary;

    /**
     * 判断是否需要修复
     */
    public boolean needsFix() {
        return !passed || !issues.isEmpty() || !missingFiles.isEmpty();
    }
}

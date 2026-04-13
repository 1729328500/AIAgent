package top.whyh.agentai.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 代码审查报告 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeReviewReport {
    /** 问题列表（含 CRITICAL 和 WARNING） */
    private List<ReviewIssue> issues = new ArrayList<>();
    /** 缺失的文件路径列表（总为 CRITICAL） */
    private List<String> missingFiles = new ArrayList<>();
    /** 审查是否通过 */
    private boolean passed;
    /** 总体建议 */
    private String summary;

    /** 只返回 CRITICAL 级别问题（需要自动修复的） */
    public List<ReviewIssue> getCriticalIssues() {
        if (issues == null) return new ArrayList<>();
        return issues.stream().filter(ReviewIssue::isCritical).collect(Collectors.toList());
    }

    /** 只返回 WARNING 级别问题（展示给用户，不自动修复） */
    public List<ReviewIssue> getWarningIssues() {
        if (issues == null) return new ArrayList<>();
        return issues.stream().filter(i -> !i.isCritical()).collect(Collectors.toList());
    }

    /**
     * 判断是否需要自动修复（只有存在 CRITICAL 问题或缺失文件才触发）
     */
    public boolean needsFix() {
        return !passed || !getCriticalIssues().isEmpty() || !missingFiles.isEmpty();
    }
}

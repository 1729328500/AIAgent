package top.whyh.agentai.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 全流程生成结果封装类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemGenerateResult {
    private String requestId;
    private String systemName;
    private String prdDocumentId;
    private String prdStoragePath;
    private String archDocumentId;
    private String archStoragePath;

    /** PRD 文档全文（内存中，供预览；用户确认后才落盘） */
    private String prdContent;

    /** 架构文档全文（内存中，供预览；用户确认后才落盘） */
    private String archContent;

    /** 生成的项目文件 Map（相对路径 -> 文件内容），用于前端预览，用户确认后再落盘 */
    private Map<String, String> projectFiles;

    /** 对应 workflow_instance.id，前端可用来跳转工作流详情 */
    private String workflowId;

    /** 用户确认保存后，后端返回的代码项目本地绝对路径 */
    private String savedProjectPath;

    /** 用户确认保存后，PRD 文档落盘路径 */
    private String savedPrdPath;

    /** 用户确认保存后，架构文档落盘路径 */
    private String savedArchPath;

    private String status;
    private String errorMsg;
    private long totalCostMs;
}

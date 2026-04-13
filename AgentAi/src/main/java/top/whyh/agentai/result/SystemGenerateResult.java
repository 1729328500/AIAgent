package top.whyh.agentai.result;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * 全流程生成结果封装类
 */
public class SystemGenerateResult {
    private final String requestId;
    private final String systemName;
    private final String prdDocumentId;
    private final String prdStoragePath;
    private final String archDocumentId;
    private final String archStoragePath;

    /** PRD 文档全文（内存中，供预览；用户确认后才落盘） */
    private final String prdContent;

    /** 架构文档全文（内存中，供预览；用户确认后才落盘） */
    private final String archContent;

    /** 生成的项目文件 Map（相对路径 -> 文件内容），用于前端预览，用户确认后再落盘 */
    private final Map<String, String> projectFiles;

    /** 对应 workflow_instance.id，前端可用来跳转工作流详情 */
    private final String workflowId;

    /** 用户确认保存后，后端返回的代码项目本地绝对路径 */
    private String savedProjectPath;

    /** 用户确认保存后，PRD 文档落盘路径 */
    private String savedPrdPath;

    /** 用户确认保存后，架构文档落盘路径 */
    private String savedArchPath;

    private final String status;
    private final String errorMsg;
    private final long totalCostMs;

    @JsonCreator
    public SystemGenerateResult(
            @JsonProperty("requestId") String requestId,
            @JsonProperty("systemName") String systemName,
            @JsonProperty("prdDocumentId") String prdDocumentId,
            @JsonProperty("prdStoragePath") String prdStoragePath,
            @JsonProperty("archDocumentId") String archDocumentId,
            @JsonProperty("archStoragePath") String archStoragePath,
            @JsonProperty("prdContent") String prdContent,
            @JsonProperty("archContent") String archContent,
            @JsonProperty("projectFiles") Map<String, String> projectFiles,
            @JsonProperty("workflowId") String workflowId,
            @JsonProperty("status") String status,
            @JsonProperty("errorMsg") String errorMsg,
            @JsonProperty("totalCostMs") long totalCostMs
    ) {
        this.requestId = requestId;
        this.systemName = systemName;
        this.prdDocumentId = prdDocumentId;
        this.prdStoragePath = prdStoragePath;
        this.archDocumentId = archDocumentId;
        this.archStoragePath = archStoragePath;
        this.prdContent = prdContent;
        this.archContent = archContent;
        this.projectFiles = projectFiles;
        this.workflowId = workflowId;
        this.status = status;
        this.errorMsg = errorMsg;
        this.totalCostMs = totalCostMs;
    }

    public String getRequestId() { return requestId; }
    public String getSystemName() { return systemName; }
    public String getPrdDocumentId() { return prdDocumentId; }
    public String getPrdStoragePath() { return prdStoragePath; }
    public String getArchDocumentId() { return archDocumentId; }
    public String getArchStoragePath() { return archStoragePath; }
    public String getPrdContent() { return prdContent; }
    public String getArchContent() { return archContent; }
    public Map<String, String> getProjectFiles() { return projectFiles; }
    public String getWorkflowId() { return workflowId; }
    public String getSavedProjectPath() { return savedProjectPath; }
    public void setSavedProjectPath(String savedProjectPath) { this.savedProjectPath = savedProjectPath; }
    public String getSavedPrdPath() { return savedPrdPath; }
    public void setSavedPrdPath(String savedPrdPath) { this.savedPrdPath = savedPrdPath; }
    public String getSavedArchPath() { return savedArchPath; }
    public void setSavedArchPath(String savedArchPath) { this.savedArchPath = savedArchPath; }
    public String getStatus() { return status; }
    public String getErrorMsg() { return errorMsg; }
    public long getTotalCostMs() { return totalCostMs; }

    @Override
    public String toString() {
        return "SystemGenerateResult{" +
                "requestId='" + requestId + '\'' +
                ", systemName='" + systemName + '\'' +
                ", status='" + status + '\'' +
                ", fileCount=" + (projectFiles != null ? projectFiles.size() : 0) +
                ", totalCostMs=" + totalCostMs +
                '}';
    }
}

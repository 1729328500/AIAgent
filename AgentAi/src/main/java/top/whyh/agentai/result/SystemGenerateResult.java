package top.whyh.agentai.result;

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

    /** 生成的项目文件 Map（相对路径 -> 文件内容），用于前端预览，用户确认后再落盘 */
    private final Map<String, String> projectFiles;

    /** 用户确认保存后，后端返回的本地绝对路径（预览阶段为 null） */
    private String savedProjectPath;

    private final String status;
    private final String errorMsg;
    private final long totalCostMs;

    public SystemGenerateResult(
            String requestId,
            String systemName,
            String prdDocumentId,
            String prdStoragePath,
            String archDocumentId,
            String archStoragePath,
            Map<String, String> projectFiles,
            String status,
            String errorMsg,
            long totalCostMs
    ) {
        this.requestId = requestId;
        this.systemName = systemName;
        this.prdDocumentId = prdDocumentId;
        this.prdStoragePath = prdStoragePath;
        this.archDocumentId = archDocumentId;
        this.archStoragePath = archStoragePath;
        this.projectFiles = projectFiles;
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
    public Map<String, String> getProjectFiles() { return projectFiles; }
    public String getSavedProjectPath() { return savedProjectPath; }
    public void setSavedProjectPath(String savedProjectPath) { this.savedProjectPath = savedProjectPath; }
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

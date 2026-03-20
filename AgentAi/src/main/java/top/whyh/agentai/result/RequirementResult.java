package top.whyh.agentai.result;

/**
 * 需求分析文档生成结果封装类
 */
public class RequirementResult {
    private final String documentId;
    private final String documentContent;
    private final String storagePath;
    private final String status;
    private final String errorMsg;

    public RequirementResult(String documentId, String documentContent, String storagePath, String status, String errorMsg) {
        this.documentId = documentId;
        this.documentContent = documentContent;
        this.storagePath = storagePath;
        this.status = status;
        this.errorMsg = errorMsg;
    }

    // Getter 方法
    public String getDocumentId() {
        return documentId;
    }

    public String getDocumentContent() {
        return documentContent;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public String getStatus() {
        return status;
    }

    public String getErrorMsg() {
        return errorMsg;
    }
}
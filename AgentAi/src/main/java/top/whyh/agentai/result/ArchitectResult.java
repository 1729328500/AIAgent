package top.whyh.agentai.result;

/**
 * 架构文档生成结果封装类
 */
public class ArchitectResult {
    private final String documentId;
    private final String documentContent;
    private final String storagePath;
    private final String status;
    private final String errorMsg;

    public ArchitectResult(String documentId, String documentContent, String storagePath, String status, String errorMsg) {
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
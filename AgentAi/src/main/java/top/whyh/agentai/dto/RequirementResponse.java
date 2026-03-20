package top.whyh.agentai.dto;

// 响应结果类
public class RequirementResponse {
    private String documentId;        // 服务端自动生成的唯一文档ID
    private String documentContent;   // 生成的需求文档内容
    private String storagePath;       // 文档存储的本地路径

    // Getter & Setter
    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getDocumentContent() {
        return documentContent;
    }

    public void setDocumentContent(String documentContent) {
        this.documentContent = documentContent;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }
}
package top.whyh.agentai.result;

/**
 * 全流程生成结果封装类（增强字段、完善toString）
 */
public class SystemGenerateResult {
    private final String requestId;        // 请求ID（链路追踪）
    private final String systemName;       // 系统名称
    private final String prdDocumentId;    // PRD文档ID
    private final String prdStoragePath;   // PRD存储路径
    private final String archDocumentId;   // 架构文档ID
    private final String archStoragePath;  // 架构文档存储路径

    // 👇 新增字段：代码存储路径
    private final String codeStoragePath;  // 生成的代码在本地的绝对路径

    private final String status;           // 执行状态（success/fail）
    private final String errorMsg;         // 错误信息
    private final long totalCostMs;        // 总耗时（毫秒）

    // 👇 更新构造函数：增加 codeStoragePath 参数（第7个位置）
    public SystemGenerateResult(
            String requestId,
            String systemName,
            String prdDocumentId,
            String prdStoragePath,
            String archDocumentId,
            String archStoragePath,
            String codeStoragePath,   // ← 新增
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
        this.codeStoragePath = codeStoragePath; // ← 赋值
        this.status = status;
        this.errorMsg = errorMsg;
        this.totalCostMs = totalCostMs;
    }

    // Getters
    public String getRequestId() { return requestId; }
    public String getSystemName() { return systemName; }
    public String getPrdDocumentId() { return prdDocumentId; }
    public String getPrdStoragePath() { return prdStoragePath; }
    public String getArchDocumentId() { return archDocumentId; }
    public String getArchStoragePath() { return archStoragePath; }

    // 👇 新增 getter
    public String getCodeStoragePath() { return codeStoragePath; }

    public String getStatus() { return status; }
    public String getErrorMsg() { return errorMsg; }
    public long getTotalCostMs() { return totalCostMs; }

    // 重写 toString（包含 codeStoragePath）
    @Override
    public String toString() {
        return "SystemGenerateResult{" +
                "requestId='" + requestId + '\'' +
                ", systemName='" + systemName + '\'' +
                ", prdDocumentId='" + prdDocumentId + '\'' +
                ", prdStoragePath='" + prdStoragePath + '\'' +
                ", archDocumentId='" + archDocumentId + '\'' +
                ", archStoragePath='" + archStoragePath + '\'' +
                ", codeStoragePath='" + codeStoragePath + '\'' + // ← 新增
                ", status='" + status + '\'' +
                ", errorMsg='" + errorMsg + '\'' +
                ", totalCostMs=" + totalCostMs +
                '}';
    }
}
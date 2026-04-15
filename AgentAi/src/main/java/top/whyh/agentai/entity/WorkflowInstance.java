package top.whyh.agentai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("workflow_instance")
public class WorkflowInstance {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    private String requirementId;

    /** 关联 Redis 任务 ID，用于从工作流详情跳转预览 */
    private String taskId;

    /** 系统显示名称（如"博客系统"） */
    private String workflowName;

    /** 当前正在执行的步骤名 */
    private String currentStep;

    private String workflowType;

    private String status;

    /** 最终生成结果的 JSON 序列化，包含 projectFiles、prdContent、archContent 等，供历史记录查看预览 */
    private String resultJson;

    /** 沙箱状态：none / deploying / running / failed */
    private String sandboxStatus;

    /** E2B 沙箱 ID */
    private String sandboxId;

    /** E2B 沙箱预览 URL */
    private String sandboxUrl;

    /** 沙箱部署错误信息 */
    private String sandboxError;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}

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

    private String requirementId;

    /** 关联 Redis 任务 ID，用于从工作流详情跳转预览 */
    private String taskId;

    /** 系统显示名称（如"博客系统"） */
    private String workflowName;

    /** 当前正在执行的步骤名 */
    private String currentStep;

    private String workflowType;

    private String status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}

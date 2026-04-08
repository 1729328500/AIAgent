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

    private String workflowType;

    private String status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}

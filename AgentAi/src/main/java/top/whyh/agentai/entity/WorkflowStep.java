package top.whyh.agentai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("workflow_step")
public class WorkflowStep {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String workflowId;

    private String stepName;

    private String stepDescription;

    private String agentId;

    private String status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer duration;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}

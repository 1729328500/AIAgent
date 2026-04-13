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

    /** 步骤输出内容（文档类存全文；代码类存文件数量摘要；超 20000 字符时截断） */
    private String outputData;

    private String agentId;

    private String status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer duration;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}

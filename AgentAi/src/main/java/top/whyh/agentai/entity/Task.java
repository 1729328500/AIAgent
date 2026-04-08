package top.whyh.agentai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("task")
public class Task {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String workflowInstanceId;

    private String agentId;

    private String taskName;

    private String taskDescription;

    private String status;

    private String inputData;

    private String outputData;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}

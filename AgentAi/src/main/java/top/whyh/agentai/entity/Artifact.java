package top.whyh.agentai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("artifact")
public class Artifact {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String taskId;

    private String workflowId;

    private String artifactType;

    private String name;

    /** 存储实际内容，如 PRD 文本、架构图文本或项目文件 JSON */
    private String content;

    private Long fileSize;

    private String status;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}

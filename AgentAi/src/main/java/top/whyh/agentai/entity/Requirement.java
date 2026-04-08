package top.whyh.agentai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("requirement")
public class Requirement {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    private String title;

    private String description;

    private String status;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}

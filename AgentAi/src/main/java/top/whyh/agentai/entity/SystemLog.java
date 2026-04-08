package top.whyh.agentai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("system_log")
public class SystemLog {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String userId;
    private String action;
    private String message;
    private LocalDateTime createdTime;
}

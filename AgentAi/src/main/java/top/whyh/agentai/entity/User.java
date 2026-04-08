package top.whyh.agentai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String username;
    private String password;
    private String avatar;
    private String email;
    private String realName;
    private String status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}

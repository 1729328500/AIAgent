package top.whyh.agentai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("agent")
public class Agent {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 智能体名称 */
    private String name;

    /** 智能体角色（pm/architect/developer/qa/devops），对应 AgentDefinition.agentCode */
    private String role;

    /** 智能体能力（JSON格式） */
    private String capabilities;

    /** 效率评分（0-100） */
    private BigDecimal efficiencyScore;

    /** 成功率（0-100） */
    private BigDecimal successRate;

    /** active / inactive */
    private String status;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}

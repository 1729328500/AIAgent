package top.whyh.agentai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import top.whyh.agentai.entity.Agent;

@Mapper
public interface AgentMapper extends BaseMapper<Agent> {
}

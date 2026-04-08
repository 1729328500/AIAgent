package top.whyh.agentai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.whyh.agentai.entity.SystemLog;
import top.whyh.agentai.mapper.SystemLogMapper;
import top.whyh.starter.common.result.Result;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class SystemLogController {

    private final SystemLogMapper systemLogMapper;

    @GetMapping("/page")
    public Result<Page<SystemLog>> getLogsByPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String action) {

        Page<SystemLog> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SystemLog> wrapper = new LambdaQueryWrapper<>();

        if (userId != null && !userId.isEmpty()) {
            wrapper.eq(SystemLog::getUserId, userId);
        }
        if (action != null && !action.isEmpty()) {
            wrapper.like(SystemLog::getAction, action);
        }

        wrapper.orderByDesc(SystemLog::getCreatedTime);
        return Result.success(systemLogMapper.selectPage(page, wrapper));
    }

    @GetMapping("/{id}")
    public Result<SystemLog> getLogById(@PathVariable String id) {
        return Result.success(systemLogMapper.selectById(id));
    }
}

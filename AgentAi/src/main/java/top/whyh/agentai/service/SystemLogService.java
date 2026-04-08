package top.whyh.agentai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import top.whyh.agentai.entity.SystemLog;
import top.whyh.agentai.mapper.SystemLogMapper;
import top.whyh.agentai.utils.SecurityUtils;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemLogService {

    private final SystemLogMapper systemLogMapper;
    private final ObjectMapper objectMapper;

    public void saveLog(String action, String message) {
        try {
            String userId = SecurityUtils.isAuthenticated() ? SecurityUtils.getCurrentUserId() : "anonymous";
            SystemLog systemLog = new SystemLog();
            systemLog.setId(UUID.randomUUID().toString());
            systemLog.setUserId(userId);
            systemLog.setAction(action);
            systemLog.setMessage(message);
            systemLogMapper.insert(systemLog);
        } catch (Exception e) {
            log.error("保存系统日志失败", e);
        }
    }

    public void saveOperationLog(String module, String type, String description, HttpServletRequest request, Object result) {
        try {
            String userId = SecurityUtils.isAuthenticated() ? SecurityUtils.getCurrentUserId() : "anonymous";
            String message = String.format("[%s] %s - %s | Method: %s %s | Result: %s",
                    module, type, description,
                    request.getMethod(), request.getRequestURI(),
                    result != null ? "success" : "null");

            SystemLog systemLog = new SystemLog();
            systemLog.setId(UUID.randomUUID().toString());
            systemLog.setUserId(userId);
            systemLog.setAction(type);
            systemLog.setMessage(message);
            systemLogMapper.insert(systemLog);
        } catch (Exception e) {
            log.error("保存操作日志失败", e);
        }
    }
}

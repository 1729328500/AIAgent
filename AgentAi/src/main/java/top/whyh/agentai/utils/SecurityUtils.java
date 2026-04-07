package top.whyh.agentai.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import top.whyh.agentai.exception.ServerException;
import top.whyh.starter.common.result.ResultCode;


@Slf4j
public class SecurityUtils {

    /**
     * 获取当前认证的用户 ID
     *
     * @return 用户 ID
     * @throws ServerException 用户未认证时抛出异常
     */
    public static Long getCurrentUserId() {
        Authentication authentication = getCurrentAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            log.warn("用户未认证，无法获取用户 ID");
            throw new ServerException(ResultCode.UNAUTHORIZED);
        }
        try {
            return (Long) authentication.getPrincipal();
        } catch (ClassCastException e) {
            log.error("获取用户 ID 失败，principal 类型不是 Long: {}", authentication.getPrincipal().getClass(), e);
            throw new ServerException(ResultCode.UNAUTHORIZED);
        }
    }

    /**
     * 获取当前认证对象
     *
     * @return Authentication 对象，可能为 null
     */
    public static Authentication getCurrentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    /**
     * 检查当前是否已认证
     *
     * @return true 表示已认证，false 表示未认证
     */
    public static boolean isAuthenticated() {
        Authentication authentication = getCurrentAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }
}
package top.whyh.agentai.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import top.whyh.agentai.exception.ServerException;
import top.whyh.agentai.security.UserPrincipal;
import top.whyh.starter.common.result.ResultCode;

@Slf4j
public class SecurityUtils {

    public static String getCurrentUserId() {
        Authentication authentication = getCurrentAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            log.warn("用户未认证，无法获取用户 ID");
            throw new ServerException(ResultCode.UNAUTHORIZED);
        }
        if (!(authentication.getPrincipal() instanceof UserPrincipal)) {
            log.warn("用户未认证或为匿名用户，principal 类型: {}", authentication.getPrincipal().getClass());
            throw new ServerException(ResultCode.UNAUTHORIZED);
        }
        try {
            UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
            return principal.getUserId();
        } catch (ClassCastException e) {
            log.error("获取用户 ID 失败，principal 类型错误: {}", authentication.getPrincipal().getClass(), e);
            throw new ServerException(ResultCode.UNAUTHORIZED);
        }
    }

    public static Authentication getCurrentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    public static boolean isAuthenticated() {
        Authentication authentication = getCurrentAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        // Check if it's a real user (not anonymous)
        return authentication.getPrincipal() instanceof UserPrincipal;
    }
}

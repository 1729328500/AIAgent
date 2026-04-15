package top.whyh.agentai.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import top.whyh.agentai.service.TokenService;
import top.whyh.agentai.utils.JwtUtils;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserDetailsServiceImpl userDetailsService;
    private final TokenService tokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        log.debug("Resolve token for URI {}: {}", request.getRequestURI(), token != null ? "exists" : "missing");

        if (StringUtils.hasText(token) && jwtUtils.validateToken(token)) {
            String userId = jwtUtils.getUserIdFromToken(token);

            // 检查 Redis 中是否存在该 Token（防止已登出或修改密码后的 Token 继续使用）
            if (!tokenService.validateToken(userId, token)) {
                log.warn("Token in Redis is invalid or expired for userId: {}", userId);
                chain.doFilter(request, response);
                return;
            }

            // load by userId stored as username key
            try {
                UserDetails userDetails = userDetailsService.loadUserByUserId(userId);
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("Successfully authenticated userId: {}", userId);
            } catch (Exception e) {
                log.error("Authentication failed for userId: {}", userId, e);
            }
        }
        chain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        // SSE (EventSource) 无法设置请求头，支持通过 URL 查询参数传递 token
        String tokenParam = request.getParameter("token");
        if (StringUtils.hasText(tokenParam)) {
            return tokenParam;
        }
        return null;
    }
}

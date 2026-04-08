package top.whyh.agentai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.whyh.agentai.dto.LoginRequest;
import top.whyh.agentai.dto.LoginResponse;
import top.whyh.agentai.dto.RegisterRequest;
import top.whyh.agentai.entity.User;
import top.whyh.agentai.exception.ServerException;
import top.whyh.agentai.mapper.UserMapper;
import top.whyh.agentai.security.UserPrincipal;
import top.whyh.agentai.utils.JwtUtils;
import top.whyh.starter.common.result.ResultCode;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    @Transactional
    public LoginResponse register(RegisterRequest request) {
        if (userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())) != null) {
            throw new ServerException(ResultCode.BUSINESS_ERROR.getCode(), "用户名已存在");
        }

        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setRealName(request.getRealName());
        user.setStatus("active");

        userMapper.insert(user);

        String token = jwtUtils.generateToken(user.getId());
        tokenService.saveToken(user.getId(), token);  // ★ 存入 Redis

        return new LoginResponse(token, user.getId(), user.getUsername());
    }

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String token = jwtUtils.generateToken(principal.getUserId());
        tokenService.saveToken(principal.getUserId(), token);  // ★ 存入 Redis

        return new LoginResponse(token, principal.getUserId(), principal.getUsername());
    }

    /**
     * 登出：删除 Redis 中的 Token
     */
    public void logout(String userId) {
        tokenService.deleteToken(userId);
        log.info("用户登出成功 | userId: {}", userId);
    }
}

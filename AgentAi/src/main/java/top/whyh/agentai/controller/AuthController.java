package top.whyh.agentai.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.whyh.agentai.dto.LoginRequest;
import top.whyh.agentai.dto.LoginResponse;
import top.whyh.agentai.dto.RegisterRequest;
import top.whyh.agentai.service.AuthService;
import top.whyh.agentai.utils.SecurityUtils;
import top.whyh.starter.common.result.Result;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Result<LoginResponse> register(@RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout(SecurityUtils.getCurrentUserId());
        return Result.success();
    }
}

package top.whyh.agentai.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import top.whyh.agentai.entity.User;
import top.whyh.agentai.service.UserService;
import top.whyh.agentai.utils.SecurityUtils;
import top.whyh.starter.common.result.Result;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestBody ChangePasswordRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        userService.changePassword(userId, request.getOldPassword(), request.getNewPassword());
        return Result.success();
    }

    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody UpdateProfileRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        userService.updateProfile(userId, request.getAvatar(), request.getEmail(), request.getRealName());
        return Result.success();
    }

    @GetMapping("/profile")
    public Result<User> getProfile() {
        String userId = SecurityUtils.getCurrentUserId();
        User user = userService.getUserById(userId);
        // 清除密码字段
        user.setPassword(null);
        return Result.success(user);
    }

    @PutMapping("/{userId}/status")
    public Result<Void> updateUserStatus(@PathVariable String userId, @RequestBody UpdateStatusRequest request) {
        userService.updateUserStatus(userId, request.getStatus());
        return Result.success();
    }

    @Data
    public static class ChangePasswordRequest {
        private String oldPassword;
        private String newPassword;
    }

    @Data
    public static class UpdateProfileRequest {
        private String avatar;
        private String email;
        private String realName;
    }

    @Data
    public static class UpdateStatusRequest {
        private String status;
    }
}

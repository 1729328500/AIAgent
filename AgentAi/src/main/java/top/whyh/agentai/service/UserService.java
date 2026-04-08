package top.whyh.agentai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import top.whyh.agentai.entity.User;
import top.whyh.agentai.mapper.UserMapper;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public void changePassword(String userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("原密码错误");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);

        // 强制下线
        tokenService.deleteToken(userId);
    }

    public void updateProfile(String userId, String avatar, String email, String realName) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        if (avatar != null) {
            user.setAvatar(avatar);
        }
        if (email != null) {
            user.setEmail(email);
        }
        if (realName != null) {
            user.setRealName(realName);
        }

        userMapper.updateById(user);
    }

    public User getUserById(String userId) {
        return userMapper.selectById(userId);
    }

    public void updateUserStatus(String userId, String status) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        user.setStatus(status);
        userMapper.updateById(user);

        // 如果禁用用户，强制下线
        if ("disabled".equals(status)) {
            tokenService.deleteToken(userId);
        }
    }
}

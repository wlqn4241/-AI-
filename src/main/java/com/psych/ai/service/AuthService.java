package com.psych.ai.service;

import com.psych.ai.common.BusinessException;
import com.psych.ai.common.JwtUtil;
import com.psych.ai.entity.User;
import com.psych.ai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    /** BCrypt 加密：即使数据库泄露也无法还原明文密码（优化点） */
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public Map<String, Object> register(String username, String password, String nickname) {
        if (username == null || !username.matches("^[a-zA-Z0-9_]{3,20}$")) {
            throw new BusinessException("用户名需为 3-20 位字母、数字或下划线");
        }
        if (password == null || password.length() < 6 || password.length() > 32) {
            throw new BusinessException("密码长度需为 6-32 位");
        }
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException("用户名已被注册");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(encoder.encode(password));
        user.setNickname(nickname == null || nickname.isBlank() ? "用户" + username : nickname);
        userRepository.save(user);
        return loginPayload(user);
    }

    public Map<String, Object> login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException("用户名或密码错误"));
        if (!encoder.matches(password, user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        return loginPayload(user);
    }

    private Map<String, Object> loginPayload(User user) {
        return Map.of(
                "token", jwtUtil.generate(user.getId(), user.getUsername()),
                "userId", user.getId(),
                "nickname", user.getNickname() == null ? user.getUsername() : user.getNickname()
        );
    }
}

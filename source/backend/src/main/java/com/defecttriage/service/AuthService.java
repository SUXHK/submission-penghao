package com.defecttriage.service;

import com.defecttriage.common.BusinessException;
import com.defecttriage.common.UnauthorizedException;
import com.defecttriage.config.JwtUtil;
import com.defecttriage.dto.*;
import com.defecttriage.entity.User;
import com.defecttriage.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    public LoginResponse register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new BusinessException("用户名已存在");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setDisplayName(req.getDisplayName());
        user.setRole(req.getRole());
        user = userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getRole().name());
        return new LoginResponse(token, user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }

    public LoginResponse login(LoginRequest req) {
        User user = userRepository.findByUsername(req.getUsername())
                .orElseThrow(() -> new UnauthorizedException("用户名或密码错误"));
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("用户名或密码错误");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getRole().name());
        return new LoginResponse(token, user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }

    public UserInfo getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("用户不存在"));
        return new UserInfo(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }
}

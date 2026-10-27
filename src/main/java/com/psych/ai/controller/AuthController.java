package com.psych.ai.controller;

import com.psych.ai.common.ApiResponse;
import com.psych.ai.dto.Dtos.LoginReq;
import com.psych.ai.dto.Dtos.RegisterReq;
import com.psych.ai.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody RegisterReq req) {
        return ApiResponse.ok(authService.register(req.username(), req.password(), req.nickname()));
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginReq req) {
        return ApiResponse.ok(authService.login(req.username(), req.password()));
    }
}

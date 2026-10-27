package com.psych.ai.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class Dtos {
    private Dtos() {
    }

    public record LoginReq(@NotBlank(message = "用户名不能为空") String username,
                           @NotBlank(message = "密码不能为空") String password) {
    }

    public record RegisterReq(@NotBlank(message = "用户名不能为空") String username,
                              @NotBlank(message = "密码不能为空") String password,
                              String nickname) {
    }

    public record ChatSendReq(@NotBlank(message = "消息不能为空") String content) {
    }

    public record EmotionReq(String date, Integer mood, List<String> tags, String content) {
    }

    public record AssessmentSubmitReq(List<Integer> answers) {
    }
}

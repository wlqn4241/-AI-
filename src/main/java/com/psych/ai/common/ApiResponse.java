package com.psych.ai.common;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 统一 API 响应包装 */
@Data
@AllArgsConstructor
public class ApiResponse<T> {
    private int code;       // 0 成功，非 0 失败
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(0, "success", null);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return error(500, message);
    }
}

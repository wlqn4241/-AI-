package com.psych.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** DeepSeek 配置项（集中管理，支持环境变量覆盖） */
@Data
@Component
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekProperties {
    private String apiKey;
    private String baseUrl = "https://api.deepseek.com";
    private String model = "deepseek-chat";
    private int connectTimeoutSeconds = 10;
    private int readTimeoutSeconds = 60;
    private int streamTimeoutSeconds = 90;
    private int historyLimit = 20;
}

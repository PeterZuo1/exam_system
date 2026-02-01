package com.atguigu.exam.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @Title: KimiProperties
 * @Author zuolizhi
 * @Package com.atguigu.exam.config.properties
 * @Date 2026/2/1 18:57
 */
@Data
@ConfigurationProperties(prefix = "kimi.api")
public class KimiProperties {
    private String apiKey;
    private String apiUrl;
    private String model;
    private Double temperature;//double类型
    private Integer maxTokens;//返回长度
}

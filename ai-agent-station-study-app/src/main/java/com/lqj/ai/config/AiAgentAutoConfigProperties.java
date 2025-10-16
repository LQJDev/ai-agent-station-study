package com.lqj.ai.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * @Author 李岐鉴
 * @Date 2025/10/11
 * @Description AI Agent 自动装配配置属性
 */
@Data
@ConfigurationProperties(prefix = "spring.ai.agent.auto-config")
public class AiAgentAutoConfigProperties {
    /**
     * 是否启用AI Agent自动装配
     */
    private boolean enabled = false;

    /**
     * AI Agent自动装配的AI客户端ID列表
     */
    private List<String> clientIds;
}

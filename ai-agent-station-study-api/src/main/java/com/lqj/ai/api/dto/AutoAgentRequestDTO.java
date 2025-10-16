package com.lqj.ai.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * @Author 李岐鉴
 * @Date 2025/10/11
 * @Description 请求
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AutoAgentRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * AI智能体ID
     */
    private String aiAgentId;

    /**
     * 用户信息
     */
    private String message;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 最大步骤数
     */
    private Integer maxStep;
}

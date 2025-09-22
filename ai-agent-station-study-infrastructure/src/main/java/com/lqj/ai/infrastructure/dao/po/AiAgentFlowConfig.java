package com.lqj.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @Author 李岐鉴
 * @Date 2025/9/21
 * @Description AiAgentFlowConfig 类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAgentFlowConfig {


    /**
     * 主键
     */
    private Long id;

    /**
     * 智能体id
     */
    private Long agentId;

    /**
     * 客户端id
     */
    private Long clientId;

    /**
     * 顺序
     */
    private Integer sequence;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}

package com.lqj.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @Author 李岐鉴
 * @Date 2025/9/21
 * @Description AiAgent 类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AiAgent {


    /**
     * 主键
     */
    private Long id;

    /**
     * 智能体ID
     */
    private String agentId;

    /**
     * 智能体名称
     */
    private String agentName;

    /**
     * 智能体描述
     */
    private String description;

    /**
     * 渠道类型
     */
    private String channel;

    /**
     * 状态( 0: 禁用 1: 启用)
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */

    private LocalDateTime updateTime;

}

package com.lqj.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @Author 李岐鉴
 * @Date 2025/9/21
 * @Description AiAgentTaskSchedule 类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAgentTaskSchedule {

    /**
     * id
     */
    private Long id;

    /**
     * 智能体id
     */
    private Long agentId;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 任务描述
     */
    private String description;

    /**
     * 时间表达式
     */
    private String cronExpression;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 任务参数(json)
     */
    private String taskParam;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;


}

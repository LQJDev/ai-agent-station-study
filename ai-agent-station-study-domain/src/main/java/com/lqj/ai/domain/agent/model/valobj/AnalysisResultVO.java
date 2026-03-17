package com.lqj.ai.domain.agent.model.valobj;

import lombok.Data;

/**
 * @Author 李岐鉴
 * @Date 2026/3/11
 * @Description AnalysisResultVO 类
 */
@Data
public class AnalysisResultVO {

    /**
     * 任务状态分析
     */
    private String analysis;

    /**
     * 执行历史评估
     */
    private String historyEvaluation;

    /**
     * 下一步策略
     */
    private String nextStrategy;

    /**
     * 完成度
     */
    private Integer progress;

    /**
     * CONTINUE / COMPLETED
     */
    private String taskStatus;
}

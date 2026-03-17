package com.lqj.ai.domain.agent.model.valobj;

import lombok.Data;

/**
 * @Author 李岐鉴
 * @Date 2026/3/12
 * @Description ExecutionSummaryVO 类
 */
@Data
public class ExecutionSummaryVO {
    /** 总结标题 */
    private String summaryTitle;

    /** 执行历史 */
    private String executionHistory;

    /** 总结分析文本 */
    private String summaryText;

    /** 是否完成 */
    private boolean completed;
}

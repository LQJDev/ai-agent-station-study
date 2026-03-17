package com.lqj.ai.domain.agent.model.valobj;

import lombok.Data;

import java.util.List;

/**
 * @Author 李岐鉴
 * @Date 2026/3/11
 * @Description ExecutionResultVO 类
 */
@Data
public class ExecutionResultVO {

    /**
     * 执行目标
     */
    private String executionTarget;

    /**
     * 执行步骤
     */
    private List<String> executionProcess;

    /**
     * 执行结果
     */
    private String executionResult;
}

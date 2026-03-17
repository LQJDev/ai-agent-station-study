package com.lqj.ai.domain.agent.model.valobj;

import lombok.Data;

import java.util.Map;

/**
 * @Author 李岐鉴
 * @Date 2026/3/12
 * @Description SupervisionResultVO 类
 */
@Data
public class SupervisionResultVO {
    /** 监督目标描述 */
    private String supervisionTarget;

    /** 各项细分结果 */
    private Map<String, String> supervisionDetails;

    /** 原始完整监督文本 */
    private String fullResult;
}

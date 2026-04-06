package com.lqj.ai.domain.agent.model.valobj;

import lombok.Data;

import java.util.List;

@Data
public class SkillRouteResultVO {
    private List<String> categories;
    private Double confidence;
    private String reason;
}

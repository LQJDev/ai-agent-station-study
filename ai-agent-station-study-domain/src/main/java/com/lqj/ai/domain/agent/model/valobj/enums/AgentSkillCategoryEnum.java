package com.lqj.ai.domain.agent.model.valobj.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Set;

/**
 * @Description Agent 领域技能分类枚举
 */
@Getter
@AllArgsConstructor
public enum AgentSkillCategoryEnum {

    ELASTICSEARCH_QUERY(
            "适用于查询ES索引结构、分片状态、使用DSL或ES|QL检索ES海量数据。",
            "classpath:skills/Elasticsearch/skill_ELASTICSEARCH_QUERY.md",
            List.of("get_mappings", "search", "esql", "get_shards", "list_indices")
    ),

    DASHBOARDS_AND_FOLDERS(
            "适用于查询、检索、修改Grafana仪表盘(Dashboard)和文件夹，或获取面板图片与链接。",
            "classpath:skills/Grafana/skill_DASHBOARDS_AND_FOLDERS.md",
            List.of("get_dashboard_summary", "get_dashboard_property", "get_dashboard_by_uid", "get_dashboard_panel_queries", "search_dashboards", "search_folders", "update_dashboard")
    ),

    // ... 你可以继续添加 Prometheus, Alerting, OnCall 等枚举

    DEFAULT(
            "当用户的需求不属于上述任何一种特定的技术领域时使用。",
            null,
            List.of() // 通用任务不挂载特定的危险/重型工具
    );

    private final String description;       // 用于喂给大模型做路由判断的描述
    private final String skillDocumentPath; // skill.md 的路径
    private final List<String> allowedTools; // 该领域下允许挂载的工具名称
}
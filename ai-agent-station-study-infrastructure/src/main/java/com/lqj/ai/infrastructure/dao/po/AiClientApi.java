package com.lqj.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @Author 李岐鉴
 * @Date 2025/9/21
 * @Description AiClientApi 类
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiClientApi {

    /**
     * 主键id
     */
    private Long id;

    /**
     * API ID
     */
    private Long apiId;

    /**
     * 基础url
     */
    private String baseUrl;

    /**
     * APi密钥
     */
    private String apiKey;

    /**
     * 对话补全路径
     */
    private String completionsPath;

    /**
     * 向量补全路径
     */
    private String embeddingsPath;

    /**
     * 状态
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

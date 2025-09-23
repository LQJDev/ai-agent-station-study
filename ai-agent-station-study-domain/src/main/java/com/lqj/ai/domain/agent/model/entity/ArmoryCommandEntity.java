package com.lqj.ai.domain.agent.model.entity;

import lombok.Data;

import java.util.List;

/**
 * 装配命令
 *
 * @author 李岐鉴
 * 2025/6/27 07:26
 */
@Data
public class ArmoryCommandEntity {

    /**
     * 命令类型
     */
    private String commandType;

    /**
     * 命令索引（clientId、modelId、apiId...）
     */
    private List<String> commandIdList;

}

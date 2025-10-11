package com.lqj.ai.domain.agent.model.entity;

import com.lqj.ai.domain.agent.model.valobj.enums.AiAgentEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 装配命令
 *
 * @author 李岐鉴
 * 2025/6/27 07:26
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class  ArmoryCommandEntity {

    /**
     * 命令类型
     */
    private String commandType;

    /**
     * 命令索引（clientId、modelId、apiId...）
     */
    private List<String> commandIdList;

    /**
     * 获取加载数据策略
     *
     * @return
     */
    public String getLoadDataStrategy() {
        return AiAgentEnumVO.getByCode(commandType).getLoadDataStrategy();
    }

}

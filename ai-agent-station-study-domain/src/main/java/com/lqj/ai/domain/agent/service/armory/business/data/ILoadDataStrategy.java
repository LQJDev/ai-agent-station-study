package com.lqj.ai.domain.agent.service.armory.business.data;



import com.lqj.ai.domain.agent.model.entity.ArmoryCommandEntity;
import com.lqj.ai.domain.agent.service.armory.factory.DefaultArmoryStrategyFactory;

import java.util.List;

/**
 * 数据加载策略
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/6/27 17:16
 */
public interface ILoadDataStrategy {

    void loadData(ArmoryCommandEntity armoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext dynamicContext);

}

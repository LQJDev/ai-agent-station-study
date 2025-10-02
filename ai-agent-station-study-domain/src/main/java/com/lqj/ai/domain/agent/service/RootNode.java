package com.lqj.ai.domain.agent.service;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import com.lqj.ai.domain.agent.model.entity.ArmoryCommandEntity;
import com.lqj.ai.domain.agent.model.valobj.AiAgentEnumVO;
import com.lqj.ai.domain.agent.service.armory.business.data.ILoadDataStrategy;
import com.lqj.ai.domain.agent.service.armory.factory.DefaultArmoryStrategyFactory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * @Author 李岐鉴
 * @Date 2025/9/22
 * @Description 根节点，数据加载
 */
@Slf4j
@Service
public class RootNode extends AbstractArmorySupport{

    private final Map<String, ILoadDataStrategy> loadDataStrategyMap;

    @Resource
    private AiClientApiNode aiClientApiNode;

    public RootNode(Map<String, ILoadDataStrategy> loadDataStrategyMap) {
        this.loadDataStrategyMap = loadDataStrategyMap;
    }

    @Override
    protected void multiThread(ArmoryCommandEntity requestParameter, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws ExecutionException, InterruptedException, TimeoutException {
        // 加载数据
        ILoadDataStrategy iLoadDataStrategy = loadDataStrategyMap.get(requestParameter.getLoadDataStrategy());
        iLoadDataStrategy.loadData(requestParameter, dynamicContext);
    }

    @Override
    protected String doApply(ArmoryCommandEntity requestParamter, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Ai agent 构建，数据加载节点{}", JSON.toJSONString(requestParamter));
        return this.router(requestParamter, dynamicContext);
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext, String> get(ArmoryCommandEntity armoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return aiClientApiNode;
    }
}

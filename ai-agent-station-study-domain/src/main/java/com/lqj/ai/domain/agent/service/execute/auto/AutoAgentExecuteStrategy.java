package com.lqj.ai.domain.agent.service.execute.auto;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.lqj.ai.domain.agent.model.entity.ExecuteCommandEntity;
import com.lqj.ai.domain.agent.service.execute.IExecuteStrategy;
import com.lqj.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @Author 李岐鉴
 * @Date 2025/10/6
 * @Description 自动执行策略
 */
@Service
@Slf4j
public class AutoAgentExecuteStrategy implements IExecuteStrategy {

    @Resource
    private DefaultAutoAgentExecuteStrategyFactory defaultAutoAgentExecuteStrategyFactory;

    @Override
    public void execute(ExecuteCommandEntity requestParameter) throws Exception {

        StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> executeHandler
        = defaultAutoAgentExecuteStrategyFactory.armoryStrategyHandler();
        String apply = executeHandler.apply(requestParameter, new DefaultAutoAgentExecuteStrategyFactory.DynamicContext());
        log.info("执行结果：{}", apply);
    }
}

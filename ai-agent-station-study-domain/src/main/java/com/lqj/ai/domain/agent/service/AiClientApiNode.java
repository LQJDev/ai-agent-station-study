package com.lqj.ai.domain.agent.service;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import com.lqj.ai.domain.agent.model.entity.ArmoryCommandEntity;
import com.lqj.ai.domain.agent.model.valobj.AiAgentEnumVO;
import com.lqj.ai.domain.agent.model.valobj.AiClientApiVO;
import com.lqj.ai.domain.agent.service.armory.factory.DefaultArmoryStrategyFactory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Author 李岐鉴
 * @Date 2025/9/23
 * @Description OpenAI API配置节点
 */
@Service
@Slf4j
public class AiClientApiNode extends AbstractArmorySupport{

    @Resource
    private AiClientToolMcpNode aiClientToolMcpNode;

    @Override
    protected String doApply(ArmoryCommandEntity requestParameter, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Ai Agent 构建，API构建节点 {}", JSON.toJSONString(requestParameter));

        List<AiClientApiVO> aiClientApiVOList = dynamicContext.getValue(dataName());

        if (aiClientApiVOList == null || aiClientApiVOList.isEmpty()) {
            log.warn("Ai agent 构建，api 构建节点，api 列表为空");
            return router(requestParameter, dynamicContext);
        }
        for (AiClientApiVO aiClientApiVO : aiClientApiVOList) {
            OpenAiApi openAiApi = OpenAiApi.builder()
                    .baseUrl(aiClientApiVO.getBaseUrl())
                    .apiKey(aiClientApiVO.getApiKey())
                    .completionsPath(aiClientApiVO.getCompletionsPath())
                    .embeddingsPath(aiClientApiVO.getEmbeddingsPath())
                    .build();
            registerBean(beanName(aiClientApiVO.getApiId()), OpenAiApi.class, openAiApi);
        }
        return router(requestParameter, dynamicContext);
    }


    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext, String> get(ArmoryCommandEntity armoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return aiClientToolMcpNode;
    }

    @Override
    protected String beanName(String id) {
        return AiAgentEnumVO.AI_CLIENT_API.getBeanName(id);
    }

    @Override
    protected String dataName() {
        return AiAgentEnumVO.AI_CLIENT_API.getDataName();
    }
}

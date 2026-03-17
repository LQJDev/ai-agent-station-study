package com.lqj.ai.domain.agent.service.execute.auto.step;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson2.JSON;
import com.lqj.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import com.lqj.ai.domain.agent.model.entity.ExecuteCommandEntity;
import com.lqj.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import com.lqj.ai.domain.agent.model.valobj.ExecutionSummaryVO;
import com.lqj.ai.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import com.lqj.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * @Author 李岐鉴
 * @Date 2025/10/8
 * @Description 执行总结节点
 */
@Slf4j
@Service
public class Step4LogExecutionSummaryNode extends AbstractExecuteSupport {

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("\n📊 === 执行第 {} 步 ===", dynamicContext.getStep());

        // 第四阶段：执行总结
        log.info("\n📊 阶段4: 执行总结分析");

        ExecutionSummaryVO summaryVO = new ExecutionSummaryVO();
        summaryVO.setSummaryTitle("执行总结");
        summaryVO.setExecutionHistory(dynamicContext.getExecutionHistory().toString());
        summaryVO.setCompleted(dynamicContext.isCompleted());

        if (!dynamicContext.isCompleted()) {
            // 生成最终总结报告
            String summaryText = generateFinalReport(requestParameter, dynamicContext);
            summaryVO.setSummaryText(summaryText);
        } else {
            summaryVO.setSummaryText("任务已完成，无需生成额外总结");
        }

        log.info("\n📝 执行总结结果: \n{}", summaryVO.getSummaryText());
        // 保存到上下文
        dynamicContext.setValue("executionSummary", summaryVO);

        // 发送 SSE
        sendSummarySse(dynamicContext, summaryVO, requestParameter.getSessionId());

        log.info("\n🏁 动态多轮执行测试结束");

        return "ai agent execution summary completed!";
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        // 总结节点是最后一个节点，返回null表示执行结束
        return defaultStrategyHandler;
    }

    /**
     * 调用 LLM 生成总结报告
     */
    private String generateFinalReport(ExecuteCommandEntity requestParameter,
                                       DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) {
        try {
            String summaryPrompt = String.format("""
                    你是一个执行总结 Agent，请对以下任务执行历史进行总结分析，并输出 JSON：
                    
                    原始用户需求:
                    %s
                    
                    执行历史:
                    %s
                    
                    分析要求:
                    1. 总结已完成的工作内容
                    2. 分析未完成原因
                    3. 提出剩余任务建议
                    4. 评估整体执行效果
                    
                    输出 JSON 格式：
                    \\{
                      "completed_work": "...",
                      "unfinished_reasons": "...",
                      "recommendations": "...",
                      "overall_assessment": "..."
                    \\}
                    """,
                    requestParameter.getMessage(),
                    dynamicContext.getExecutionHistory().toString());

            AiAgentClientFlowConfigVO clientConfig = dynamicContext.getAiAgentClientFlowConfigVOMap()
                    .get(AiClientTypeEnumVO.TASK_ANALYZER_CLIENT.getCode());
            ChatClient chatClient = getChatClientByClientId(clientConfig.getClientId());

            String result = chatClient
                    .prompt(summaryPrompt)
                    .advisors(a -> a
                            .param(CHAT_MEMORY_CONVERSATION_ID_KEY, requestParameter.getSessionId() + "-summary")
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 50))
                    .call().content();

            return result != null ? result : "无返回总结";

        } catch (Exception e) {
            log.error("生成最终总结报告失败: {}", e.getMessage(), e);
            return "生成最终总结报告异常";
        }
    }

    /**
     * SSE 发送总结结果
     */
    private void sendSummarySse(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                ExecutionSummaryVO summaryVO,
                                String sessionId) {

        // 发送完整 JSON
        sendSse(dynamicContext, "execution_summary", JSON.toJSONString(summaryVO.getSummaryText()), sessionId);
    }

    /** 通用 SSE 方法 */
    private void sendSse(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                         String subType,
                         String content,
                         String sessionId) {
        if (subType == null || subType.isEmpty() || content == null || content.isEmpty()) return;

        AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createSupervisionSubResult(
                dynamicContext.getStep(), subType, content, sessionId);
        sendSseResult(dynamicContext, result);
    }
}

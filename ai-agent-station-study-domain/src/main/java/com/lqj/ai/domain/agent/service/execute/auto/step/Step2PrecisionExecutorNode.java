package com.lqj.ai.domain.agent.service.execute.auto.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson2.JSON;
import com.lqj.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import com.lqj.ai.domain.agent.model.entity.ExecuteCommandEntity;
import com.lqj.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import com.lqj.ai.domain.agent.model.valobj.ExecutionResultVO;
import com.lqj.ai.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import com.lqj.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @Author 李岐鉴
 * @Date 2025/10/7
 * @Description 精准执行节点
 */
@Service
@Slf4j
public class Step2PrecisionExecutorNode extends AbstractExecuteSupport{


    @Override
    protected String doApply(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("\n⚡ 阶段2: 精准任务执行");

        // 从动态上下文中获取分析结果
        String analysisResult = dynamicContext.getValue("analysis");

        if (analysisResult == null || analysisResult.trim().isEmpty()) {
            log.warn("⚠️ 分析结果为空，使用默认执行策略");
            analysisResult = "执行当前任务步骤";
        }

        String executionPrompt = String.format("""
            你是一个精准任务执行 Agent，负责根据分析师制定的策略执行任务。
            
            【用户需求】
            %s
            
            【分析师策略】
            %s
            
            【你的职责】
            根据策略执行具体任务，例如：
            - 搜索信息包括知识库里面的工具的相关文档
            - 查询监控数据 
            - 查询日志信息（search)
            - 调用工具mcp工具如es-mcp、grafana-mcp等
            - 生成分析结果
            - 如果遇到参数不确定问题，可以查询知识库寻求答案
            
            请注意：你需要真正执行任务，而不是解释任务。
            
            【重要规则】
            
            1. 必须严格按照 JSON 格式输出
            2. 只允许输出 JSON
            3. 不允许输出任何解释说明
            4. 不允许使用 ```json 或 markdown
            5. JSON 必须是合法格式
            6. 字段名称必须严格一致
            
            【JSON 输出结构】
            
            \\{
              "executionTarget": "当前执行的任务目标",
              "executionProcess": [
                "执行步骤1",
                "执行步骤2",
                "执行步骤3"
              ],
              "executionResult": "最终执行结果"
            \\}
            
            字段说明：
            
            executionTarget
            本轮执行的任务目标
            
            executionProcess  
            执行步骤列表，每个元素是一条执行步骤
            
            executionResult  
            最终执行结果，可以是总结文本或数据结果,字符串形式
            
            在返回之前，请检查 JSON 是否为合法格式。
            
            现在开始执行任务。
            """,
                requestParameter.getMessage(),
                analysisResult
        );


        // 获取对话客户端
        AiAgentClientFlowConfigVO aiAgentClientFlowConfigVO = dynamicContext.getAiAgentClientFlowConfigVOMap().get(AiClientTypeEnumVO.PRECISION_EXECUTOR_CLIENT.getCode());
        ChatClient chatClient = getChatClientByClientId(aiAgentClientFlowConfigVO.getClientId());

        String executionResult = chatClient
                .prompt(new Prompt(new UserMessage(executionPrompt)))
                .advisors(a -> a
                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, requestParameter.getSessionId())
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 1024))
                .call().content();
        log.info("LLM 精准任务执行输出: {}", executionResult);

        ExecutionResultVO executionResultVO = null;

        try {
            executionResultVO = JSON.parseObject(executionResult, ExecutionResultVO.class);
        } catch (Exception e) {
            log.error("解析执行结果失败: {}", executionResult, e);
            executionResultVO = new ExecutionResultVO();
            executionResultVO.setExecutionTarget("解析失败");
            executionResultVO.setExecutionProcess(List.of());
            executionResultVO.setExecutionResult("解析失败或格式错误");
        }


        sendExecutionResult(dynamicContext, executionResultVO, requestParameter.getSessionId());

        // 将执行结果保存到动态上下文中，供下一步使用
        dynamicContext.setValue("executionResult", executionResultVO);

        // 更新执行历史
        String stepSummary = String.format("""
                === 第 %d 步执行记录 ===
                【分析阶段】%s
                【执行阶段】%s
                """, dynamicContext.getStep(), analysisResult, executionResult);

        dynamicContext.getExecutionHistory().append(stepSummary);

        return router(requestParameter, dynamicContext);
    }

    private void sendExecutionResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                     ExecutionResultVO result, String sessionId) {
        int step = dynamicContext.getStep();

        sendSseResult(dynamicContext,
                AutoAgentExecuteResultEntity.createExecutionSubResult(
                        step,
                        "execution_target",
                        result.getExecutionTarget(),
                        sessionId
                ));

        sendSseResult(dynamicContext,
                AutoAgentExecuteResultEntity.createExecutionSubResult(
                        step,
                        "execution_process",
                        String.join("\n", result.getExecutionProcess()),
                        sessionId
                ));

        sendSseResult(dynamicContext,
                AutoAgentExecuteResultEntity.createExecutionSubResult(
                        step,
                        "execution_result",
                        result.getExecutionResult(),
                        sessionId
                ));
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return getBean("step3QualitySupervisorNode");
    }


    /**
     * 发送执行阶段细分结果到流式输出
     */
    private void sendExecutionSubResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                        String subType, String content, String sessionId) {
        // 抽取的通用判断逻辑
        if (!subType.isEmpty() && !content.isEmpty()) {
            AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createExecutionSubResult(
                    dynamicContext.getStep(), subType, content, sessionId);
            sendSseResult(dynamicContext, result);
        }
    }

}

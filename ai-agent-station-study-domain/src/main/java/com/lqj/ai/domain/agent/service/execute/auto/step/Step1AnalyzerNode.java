package com.lqj.ai.domain.agent.service.execute.auto.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson2.JSON;
import com.lqj.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import com.lqj.ai.domain.agent.model.entity.ExecuteCommandEntity;
import com.lqj.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import com.lqj.ai.domain.agent.model.valobj.AnalysisResultVO;
import com.lqj.ai.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import com.lqj.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

/**
 * @Author 李岐鉴
 * @Date 2025/10/7
 * @Description 任务分析节点
 */
@Service
@Slf4j
public class Step1AnalyzerNode extends AbstractExecuteSupport{


    @Override
    protected String doApply(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext
                                     dynamicContext) throws Exception {
        log.info("\n🎯 === 执行第 {} 步 ===", dynamicContext.getStep());

        // 第一阶段：任务分析
        log.info("\n📊 阶段1: 任务状态分析");
        // 构建 Prompt
        String analysisPrompt = buildAnalysisPrompt(requestParameter, dynamicContext);

        // 获取对话客户端
        AiAgentClientFlowConfigVO aiAgentClientFlowConfigVO = dynamicContext.getAiAgentClientFlowConfigVOMap().get(AiClientTypeEnumVO.TASK_ANALYZER_CLIENT.getCode());
        ChatClient chatClient = getChatClientByClientId(aiAgentClientFlowConfigVO.getClientId());

        String analysisResult = chatClient
                .prompt(new Prompt(new UserMessage(analysisPrompt)))
                .advisors(a -> a
                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, requestParameter.getSessionId())
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 1024))
                .call().content();

        log.info("LLM 任务状态分析输出: {}", analysisResult);

        if (analysisResult == null || analysisResult.length() == 0) {
            throw new IllegalArgumentException("LLM 返回的任务分析结果为空");
        }
        AnalysisResultVO result = parseAnalysis(analysisResult);
        updateContext(dynamicContext, result);

        // SSE输出
        streamAnalysisResult(dynamicContext, result, requestParameter.getSessionId());

        return router(requestParameter, dynamicContext);

    }

    /**
     * 解析任务分析结果
     */
    private AnalysisResultVO parseAnalysis(String content) {

        try {
            return JSON.parseObject(content, AnalysisResultVO.class);
        } catch (Exception e) {
            log.error("❌ JSON解析失败: {}", content);
            throw new RuntimeException("LLM返回JSON格式不正确", e);
        }
    }


    /**
     * 构建任务分析 Prompt
     */
    private String buildAnalysisPrompt(ExecuteCommandEntity request,
                                       DefaultAutoAgentExecuteStrategyFactory.DynamicContext context) {

        String history = context.getExecutionHistory().isEmpty()
                ? "[首次执行]"
                : context.getExecutionHistory().toString();

        log.info("请求信息:{}", JSON.toJSONString(request.getMessage()));
        log.info("历史信息:{}", JSON.toJSONString(history));

        return String.format("""
        你是一个可观测性运维分析Agent的任务规划器。

        原始用户需求:
        %s

        当前执行步骤:
        第 %d 步 (最大 %d 步)

        历史执行记录:
        %s

        当前任务:
        %s

        你的职责:
        1. 理解用户真正要解决的问题
        2. 基于当前上下文评估任务进展
        3. 可以借助知识库信息给出下一步执行策略
        4. 只负责规划，不负责执行
        5. 输出要简短、准确、可执行

        你必须遵守以下输出规则:
        1. 只能输出一个JSON对象
        2. 不要输出多个JSON对象
        3. 不要输出Markdown
        4. 不要输出解释说明
        5. 不要输出任何JSON之外的内容
        6. 如果想修改答案，请直接修改同一个JSON对象，不要再输出第二个JSON对象

        返回格式必须严格等于：
        \\{
          "analysis": "任务状态分析",
          "historyEvaluation": "执行历史评估",
          "nextStrategy": "下一步执行策略",
          "progress": 0,
          "taskStatus": "CONTINUE"
        \\}

        其中:
        - progress 必须是 0 到 100 的整数
        - taskStatus 只能是 CONTINUE 或 COMPLETED
        """,
                request.getMessage(),
                context.getStep(),
                context.getMaxStep(),
                history,
                context.getCurrentTask()
        );
    }

    /**
     * 更新上下文
     */
    private void updateContext(DefaultAutoAgentExecuteStrategyFactory.DynamicContext context,
                               AnalysisResultVO result) {

        context.setValue("analysis", result.getAnalysis());
        context.setValue("historyEvaluation", result.getHistoryEvaluation());
        context.setValue("nextStrategy", result.getNextStrategy());
        context.setValue("progress", result.getProgress());
        // Executor可以直接用
        context.setCurrentTask(result.getNextStrategy());
    }


    /**
     * 获取下一步执行策略
     */
    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity executeCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        // 如果任务已完成或达到最大步数，进入总结阶段
        if (dynamicContext.isCompleted() || dynamicContext.getStep() > dynamicContext.getMaxStep()) {
            return getBean("step4LogExecutionSummaryNode");
        }
        // 否则继续执行下一步
        return getBean("step2PrecisionExecutorNode");
    }

    /**
     * SSE输出
     */
    private void streamAnalysisResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext context,
                                      AnalysisResultVO result,
                                      String sessionId) {

        sendSection(context, "analysis_status", result.getAnalysis(), sessionId);

        sendSection(context, "analysis_history", result.getHistoryEvaluation(), sessionId);

        sendSection(context, "analysis_strategy", result.getNextStrategy(), sessionId);

        sendSection(context, "analysis_progress",
                "完成度评估: " + result.getProgress() + "%", sessionId);

        sendSection(context, "analysis_task_status",
                "任务状态: " + result.getTaskStatus(), sessionId);
    }

    /**
     * SSE输出
     */
    private void sendSection(DefaultAutoAgentExecuteStrategyFactory.DynamicContext context,
                             String type,
                             String content,
                             String sessionId) {

        if (content == null || content.isEmpty()) return;

        AutoAgentExecuteResultEntity result =
                AutoAgentExecuteResultEntity.createAnalysisSubResult(
                        context.getStep(), type, content, sessionId);

        sendSseResult(context, result);
    }

}


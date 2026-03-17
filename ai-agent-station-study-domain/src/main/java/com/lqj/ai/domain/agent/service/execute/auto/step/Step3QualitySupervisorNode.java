package com.lqj.ai.domain.agent.service.execute.auto.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson2.JSON;
import com.lqj.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import com.lqj.ai.domain.agent.model.entity.ExecuteCommandEntity;
import com.lqj.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import com.lqj.ai.domain.agent.model.valobj.ExecutionResultVO;
import com.lqj.ai.domain.agent.model.valobj.SupervisionResultVO;
import com.lqj.ai.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import com.lqj.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.HashMap;

/**
 * @Author 李岐鉴
 * @Date 2025/10/8
 * @Description 质量监督节点
 */
@Service
@Slf4j
public class Step3QualitySupervisorNode extends AbstractExecuteSupport{


    @Override
    protected String doApply(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        // 第三阶段：质量监督
        log.info("\n🔍 阶段3: 质量监督检查");
        // 从动态上下文中获取执行结果
        Object executionObj = dynamicContext.getValue("executionResult");
        String executionResult = executionObj != null ? executionObj.toString() : "";
        if (executionResult.isEmpty()) {
            log.warn("⚠️ 执行结果为空，跳过质量监督");
            return "质量监督跳过";
        }

        // LLM 提示词明确要求输出 JSON
        String supervisionPrompt = """
                你是一个质量监督 Agent。
                
                用户原始需求:
                %s
                
                执行结果:
                %s
                
                任务:
                根据执行结果严格评估是否满足用户需求。
                
                要求:
                1. 检查是否直接回答用户问题
                2. 评估内容的完整性和实用性
                3. 指出存在的问题和改进建议
                4. 给出质量评分 (1-10) 和是否通过 (PASS/FAIL/OPTIMIZE)
                
                输出:
                必须返回 JSON 格式，结构如下：
                \\{
                  "supervisionTarget": "执行结果整体评估",
                  "supervisionDetails": \\{
                    "需求匹配度": "...",
                    "内容完整性": "...",
                    "问题识别": "...",
                    "改进建议": "...",
                    "质量评分": "...",
                    "是否通过": "PASS/FAIL/OPTIMIZE"
                  \\},
                  "fullResult": "原始完整监督文本"
                \\}
                """.formatted(requestParameter.getMessage(), executionResult);
        // 获取对话客户端
        AiAgentClientFlowConfigVO aiAgentClientFlowConfigVO = dynamicContext.getAiAgentClientFlowConfigVOMap().get(AiClientTypeEnumVO.QUALITY_SUPERVISOR_CLIENT.getCode());
        ChatClient chatClient = getChatClientByClientId(aiAgentClientFlowConfigVO.getClientId());

        String supervisionResult = chatClient
                .prompt(new Prompt(new UserMessage(supervisionPrompt)))
                .advisors(a -> a
                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, requestParameter.getSessionId())
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 1024))
                .call().content();

        log.info("LLM 质量检查输出: {}", supervisionResult);

        // 解析 JSON
        SupervisionResultVO vo;
        try {
            vo = JSON.parseObject(supervisionResult, SupervisionResultVO.class);
        } catch (Exception e) {
            log.error("解析监督结果失败, 返回原始文本", e);
            vo = new SupervisionResultVO();
            vo.setSupervisionTarget("执行结果整体评估");
            vo.setSupervisionDetails(new HashMap<>());
            vo.setFullResult(supervisionResult != null ? supervisionResult : "无返回结果");
        }

        // 保存到上下文
        dynamicContext.setValue("supervisionResult", vo);

        // 发送 SSE 子结果和完整结果
        sendSupervisionResults(dynamicContext, vo, requestParameter.getSessionId());

        // 根据是否通过更新任务状态
        String passStatus = vo.getSupervisionDetails().getOrDefault("是否通过", "PASS");
        if ("FAIL".equals(passStatus)) {
            log.info("❌ 质量检查未通过，需要重新执行");
            dynamicContext.setCurrentTask("根据质量监督的建议重新执行任务");
        } else if ("OPTIMIZE".equals(passStatus)) {
            log.info("🔧 质量检查建议优化，继续改进");
            dynamicContext.setCurrentTask("根据质量监督的建议优化执行结果");
        } else {
            log.info("✅ 质量检查通过");
            dynamicContext.setCompleted(true);
        }

        // 更新执行历史
        String stepSummary = """
                === 第 %d 步完整记录 ===
                【分析阶段】%s
                【执行阶段】%s
                【监督阶段】%s
                """.formatted(dynamicContext.getStep(),
                dynamicContext.getValue("analysisResult"),
                executionResult,
                vo.getFullResult());

        dynamicContext.getExecutionHistory().append(stepSummary);

        // 增加步骤计数
        dynamicContext.setStep(dynamicContext.getStep() + 1);

        // 否则继续下一轮执行，返回到Step1AnalyzerNode
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity, DefaultAutoAgentExecuteStrategyFactory.DynamicContext, String> get(ExecuteCommandEntity requestParameter, DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        // 如果任务已完成或达到最大步数，进入总结阶段
        if (dynamicContext.isCompleted() || dynamicContext.getStep() > dynamicContext.getMaxStep()) {
            return getBean("step4LogExecutionSummaryNode");
        }
        // 否则返回到Step1AnalyzerNode进行下一轮分析
        return getBean("step1AnalyzerNode");
    }

    /** 发送 JSON 格式的监督结果 */
    private void sendSupervisionResults(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                        SupervisionResultVO vo, String sessionId) {

        if (vo.getSupervisionDetails() != null) {
            vo.getSupervisionDetails().forEach((key, value) -> sendSse(dynamicContext, key, value, sessionId));
        }

        // 发送完整监督结果
        sendSse(dynamicContext, "监督结果", vo.getFullResult(), sessionId);
    }

    /** SSE 发送抽象方法 */
    private void sendSse(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                         String subType, String content, String sessionId) {
        if (subType == null || content == null || subType.isEmpty() || content.isEmpty()) return;

        AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createSupervisionSubResult(
                dynamicContext.getStep(), subType, content, sessionId);
        sendSseResult(dynamicContext, result);
    }

}

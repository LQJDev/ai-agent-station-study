package com.lqj.ai.domain.agent.service.execute.auto.step;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson2.JSON;
import com.lqj.ai.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import com.lqj.ai.domain.agent.model.entity.ExecuteCommandEntity;
import com.lqj.ai.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import com.lqj.ai.domain.agent.model.valobj.ExecutionResultVO;
import com.lqj.ai.domain.agent.model.valobj.SkillRouteResultVO;
import com.lqj.ai.domain.agent.model.valobj.enums.AgentSkillCategoryEnum;
import com.lqj.ai.domain.agent.model.valobj.enums.AiClientTypeEnumVO;
import com.lqj.ai.domain.agent.service.execute.auto.step.factory.DefaultAutoAgentExecuteStrategyFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @Author 李岐鉴
 * @Date 2025/10/7
 * @Description 精准执行节点
 */
@Service
@Slf4j
public class Step2PrecisionExecutorNode extends AbstractExecuteSupport {

    @Autowired
    private ResourceLoader resourceLoader;

    /**
     * Skill 文档缓存，避免重复 IO
     */
    private final Map<String, String> skillCache = new ConcurrentHashMap<>();

    @Override
    protected String doApply(ExecuteCommandEntity requestParameter,
                             DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {

        log.info("\n⚡ 阶段2: 精准任务执行");

        // 1. 获取分析结果
        String analysisResult = dynamicContext.getValue("analysis");
        if (analysisResult == null || analysisResult.trim().isEmpty()) {
            log.warn("⚠️ 分析结果为空，使用默认执行策略");
            analysisResult = """
                    目标：根据用户请求完成任务执行。
                    要求：优先判断当前任务所属领域，必要时调用工具获取事实依据，再输出结构化执行结果。
                    """;
        }

        // 2. 获取执行客户端
        AiAgentClientFlowConfigVO clientConfig =
                dynamicContext.getAiAgentClientFlowConfigVOMap()
                        .get(AiClientTypeEnumVO.PRECISION_EXECUTOR_CLIENT.getCode());

        ChatClient chatClient = getChatClientByClientId(clientConfig.getClientId());

        // 3. 先做技能路由
        List<AgentSkillCategoryEnum> selectedCategories =
                determineSkillCategoriesWithLLM(chatClient, requestParameter.getMessage(), analysisResult);

        log.info("🎯 LLM 路由判定结果: {}", selectedCategories);

        // 4. 合并 Skill 文档
        String mergedSkillContent = mergeSkillDocuments(selectedCategories);

        log.info("加载skill文档: {}", mergedSkillContent);

        // 5. 合并工具白名单
        String[] allowedTools = mergeAllowedTools(selectedCategories);

        log.info("🧰 本轮允许调用工具: {}", JSON.toJSONString(allowedTools));

        // 6. 构建 Prompt
        String systemPrompt = buildSystemPrompt(mergedSkillContent);

        String userPrompt = buildUserPrompt(
                requestParameter.getMessage(),
                analysisResult,
                selectedCategories,
                allowedTools
        );

        Prompt prompt = new Prompt(
                List.of(
                        new SystemMessage(systemPrompt),
                        new UserMessage(userPrompt)
                )
        );

        // 7. 正式执行
        String executionRawResult = chatClient
                .prompt(prompt)
                .advisors(a -> a
                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, requestParameter.getSessionId())
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 30))
                .call()
                .content();

        log.info("LLM 精准任务执行原始输出: {}", executionRawResult);

        // 8. 解析结果
        ExecutionResultVO executionResultVO = parseExecutionResult(executionRawResult);

        // 9. 发送执行结果
        sendExecutionResult(dynamicContext, executionResultVO, requestParameter.getSessionId());

        // 10. 保存上下文
        dynamicContext.setValue("executionResult", executionResultVO);

        String stepSummary = String.format("""
                === 第 %d 步执行记录 ===
                【分析阶段】%s
                【路由技能】%s
                【执行阶段】%s
                """,
                dynamicContext.getStep(),
                analysisResult,
                selectedCategories,
                executionRawResult
        );

        dynamicContext.getExecutionHistory().append(stepSummary);

        return router(requestParameter, dynamicContext);
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt(String mergedSkillContent) {
        return String.format("""
                你是一个精准任务执行 Agent，负责根据策略执行任务，并在需要时调用系统工具。
                
                【领域专家知识与执行规范】
                以下内容是本轮任务相关的技能文档、工具使用规范、参数构造规则和最佳实践。
                你必须严格遵守：
                ---
                %s
                ---
                
                【工具调用规则】
                1. 当任务需要外部事实、日志、指标、检索结果时，应优先调用工具获取真实数据。
                2. 如果工具参数不确定，应优先根据 skill 文档规则构造参数。
                3. 如果字段名不确定，可以先调用查询字段/映射类工具。
                4. 不要编造工具结果，不要假装调用过工具。
                
                【最终输出规则】(最高优先级)
                当你完成必要的工具调用并获得足够信息后，最终回复必须严格输出合法 JSON：
                1. 只允许输出 JSON
                2. 不允许输出 markdown
                3. 不允许输出 ```json
                4. 字段名称必须严格一致
                5. JSON 必须合法
                
                输出格式如下：
                \\{
                  "executionTarget": "当前执行的任务目标",
                  "executionProcess": [
                    "执行步骤1",
                    "执行步骤2"
                  ],
                  "executionResult": "最终执行结果"
                \\}
                """, mergedSkillContent == null || mergedSkillContent.isBlank()
                ? "正常执行通用任务逻辑。"
                : mergedSkillContent);
    }

    /**
     * 构建用户提示词
     */
    private String buildUserPrompt(String userMessage,
                                   String analysisResult,
                                   List<AgentSkillCategoryEnum> selectedCategories,
                                   String[] allowedTools) {
        return """
            【用户原始请求】
            %s

            【任务分析结果】
            %s

            【本轮选中分类】
            %s

            【本轮允许调用工具】
            %s

            请基于以上信息，严格输出一个 JSON 对象作为执行结果。
            禁止输出解释、禁止输出多段内容、禁止输出 Markdown、禁止反问。
            如果无法执行，也必须返回合法 JSON。
                输出格式如下：
                    \\{
                      "executionTarget": "当前执行的任务目标",
                      "executionProcess": [
                        "执行步骤1",
                        "执行步骤2"
                      ],
                      "executionResult": "最终执行结果"
                    \\}
            """.formatted(
                userMessage,
                analysisResult,
                JSON.toJSONString(selectedCategories),
                JSON.toJSONString(allowedTools)
        );
    }
    /**
     * 多 Skill 路由
     */
    private List<AgentSkillCategoryEnum> determineSkillCategoriesWithLLM(ChatClient chatClient,
                                                                         String userMessage,
                                                                         String analysisResult) {
        StringBuilder categoriesDesc = new StringBuilder();
        for (AgentSkillCategoryEnum category : AgentSkillCategoryEnum.values()) {
            categoriesDesc.append("- ")
                    .append(category.name())
                    .append(": ")
                    .append(category.getDescription())
                    .append("\n");
        }

        String routingPrompt = String.format("""
                你是一个意图路由器。
                请优先依据用户原始需求判断领域，分析师策略仅作为参考。
                
                【候选领域】
                %s
                
                【用户需求】
                %s
                
                【分析师策略】
                %s
                
                【输出要求】
                你必须只返回一个 JSON 对象，且不能有多个 JSON。禁止输出多个结果，禁止解释说明。
                \\{
                  "categories": ["领域枚举名1", "领域枚举名2"],
                  "confidence": 0.0,
                  "reason": "简短原因"
                \\}
                
                规则：
                1. categories 中的值必须来自候选领域枚举名
                2. 最多返回 3 个
                3. 若无法判断，返回 ["DEFAULT"]
                """, categoriesDesc, nullToEmpty(userMessage), nullToEmpty(analysisResult));

        try {
            String content = chatClient.prompt(routingPrompt).call().content();
            log.info("🧭 路由模型原始输出: {}", content);

            SkillRouteResultVO result =
                    JSON.parseObject(extractJson(content), SkillRouteResultVO.class);

            if (result == null || result.getCategories() == null || result.getCategories().isEmpty()) {
                return List.of(AgentSkillCategoryEnum.DEFAULT);
            }

            List<AgentSkillCategoryEnum> categories = result.getCategories().stream()
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .map(name -> {
                        try {
                            return AgentSkillCategoryEnum.valueOf(name);
                        } catch (Exception e) {
                            log.warn("非法技能枚举名称: {}", name);
                            return AgentSkillCategoryEnum.DEFAULT;
                        }
                    })
                    .distinct()
                    .limit(3)
                    .toList();

            return categories.isEmpty() ? List.of(AgentSkillCategoryEnum.DEFAULT) : categories;
        } catch (Exception e) {
            log.warn("LLM 路由失败，降级 DEFAULT", e);
            return List.of(AgentSkillCategoryEnum.DEFAULT);
        }
    }

    /**
     * 合并 Skill 文档
     */
    private String mergeSkillDocuments(List<AgentSkillCategoryEnum> categories) {
        if (categories == null || categories.isEmpty()) {
            return "正常执行通用任务逻辑。";
        }

        List<String> docs = new ArrayList<>();
        for (AgentSkillCategoryEnum category : categories) {
            String path = category.getSkillDocumentPath();
            String content = loadSkillDocument(path);
            if (content != null && !content.isBlank()) {
                docs.add("""
                        【技能领域：%s】
                        %s
                        """.formatted(category.name(), content));
            }
        }

        if (docs.isEmpty()) {
            return "正常执行通用任务逻辑。";
        }

        return String.join("\n\n----------------------\n\n", docs);
    }

    /**
     * 合并工具白名单
     */
    private String[] mergeAllowedTools(List<AgentSkillCategoryEnum> categories) {
        if (categories == null || categories.isEmpty()) {
            return new String[0];
        }

        LinkedHashSet<String> toolSet = new LinkedHashSet<>();
        for (AgentSkillCategoryEnum category : categories) {
            List<String> allowedTools = category.getAllowedTools();
            if (allowedTools != null && !allowedTools.isEmpty()) {
                toolSet.addAll(allowedTools);
            }
        }

        return toolSet.toArray(new String[0]);
    }

    /**
     * 加载 Skill 文档
     */
    private String loadSkillDocument(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "";
        }

        return skillCache.computeIfAbsent(path, p -> {
            try {
                Resource resource = resourceLoader.getResource(p);
                if (!resource.exists()) {
                    log.warn("找不到 Skill 文档: {}", p);
                    return "";
                }
                try (InputStream is = resource.getInputStream()) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                log.error("读取 Skill 文档异常: {}", p, e);
                return "";
            }
        });
    }

    /**
     * 解析执行结果
     */
    private ExecutionResultVO parseExecutionResult(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return buildParseFailedResult("模型返回为空");
        }

        try {
            String json = extractJson(rawContent);
            ExecutionResultVO result = JSON.parseObject(json, ExecutionResultVO.class);
            if (result == null) {
                return buildParseFailedResult("解析结果为空");
            }

            if (result.getExecutionTarget() == null) {
                result.setExecutionTarget("");
            }
            if (result.getExecutionProcess() == null) {
                result.setExecutionProcess(List.of());
            }
            if (result.getExecutionResult() == null) {
                result.setExecutionResult("");
            }

            return result;
        } catch (Exception e) {
            log.error("解析执行结果失败: {}", rawContent, e);
            return buildParseFailedResult("解析失败或格式错误: " + rawContent);
        }
    }

    private ExecutionResultVO buildParseFailedResult(String msg) {
        ExecutionResultVO resultVO = new ExecutionResultVO();
        resultVO.setExecutionTarget("解析失败");
        resultVO.setExecutionProcess(List.of());
        resultVO.setExecutionResult(msg);
        return resultVO;
    }

    /**
     * 从模型输出中提取 JSON 主体
     */
    private String extractJson(String content) {
        if (content == null || content.isBlank()) {
            return "{}";
        }

        String trimmed = content.trim();

        // 先去掉 markdown 代码块
        trimmed = trimmed.replace("```json", "")
                .replace("```JSON", "")
                .replace("```", "")
                .trim();

        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');

        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }

        return trimmed;
    }

    private String nullToEmpty(String str) {
        return str == null ? "" : str;
    }

    private void sendExecutionResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                     ExecutionResultVO result,
                                     String sessionId) {

        int step = dynamicContext.getStep();

        sendSseResult(dynamicContext,
                AutoAgentExecuteResultEntity.createExecutionSubResult(
                        step,
                        "execution_target",
                        safe(result.getExecutionTarget()),
                        sessionId
                ));

        sendSseResult(dynamicContext,
                AutoAgentExecuteResultEntity.createExecutionSubResult(
                        step,
                        "execution_process",
                        result.getExecutionProcess() == null
                                ? ""
                                : String.join("\n", result.getExecutionProcess()),
                        sessionId
                ));

        sendSseResult(dynamicContext,
                AutoAgentExecuteResultEntity.createExecutionSubResult(
                        step,
                        "execution_result",
                        safe(result.getExecutionResult()),
                        sessionId
                ));
    }

    private String safe(String str) {
        return str == null ? "" : str;
    }

    /**
     * 发送执行阶段细分结果到流式输出
     */
    private void sendExecutionSubResult(DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext,
                                        String subType,
                                        String content,
                                        String sessionId) {
        if (subType != null && !subType.isEmpty() && content != null && !content.isEmpty()) {
            AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createExecutionSubResult(
                    dynamicContext.getStep(),
                    subType,
                    content,
                    sessionId
            );
            sendSseResult(dynamicContext, result);
        }
    }

    @Override
    public StrategyHandler<ExecuteCommandEntity,
            DefaultAutoAgentExecuteStrategyFactory.DynamicContext,
            String> get(ExecuteCommandEntity requestParameter,
                        DefaultAutoAgentExecuteStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return getBean("step3QualitySupervisorNode");
    }
}
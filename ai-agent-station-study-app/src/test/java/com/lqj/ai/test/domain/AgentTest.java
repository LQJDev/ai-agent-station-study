package com.lqj.ai.test.domain;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import com.lqj.ai.domain.agent.model.entity.ArmoryCommandEntity;
import com.lqj.ai.domain.agent.model.valobj.enums.AiAgentEnumVO;
import com.lqj.ai.domain.agent.service.armory.factory.DefaultArmoryStrategyFactory;
import io.modelcontextprotocol.client.McpSyncClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.Map;

/**
 * @Author 李岐鉴
 * @Date 2025/9/25
 * @Description AgentTest 类
 */
@Slf4j
@SpringBootTest
@RunWith(SpringRunner.class)
public class AgentTest {

    @Resource
    private DefaultArmoryStrategyFactory defaultArmoryStrategyFactory;

    @Resource
    private ApplicationContext applicationContext;

    @Test
    public void test_aiClientApiNode() throws Exception {
        StrategyHandler<ArmoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext, String> armoryStrategyHandler = defaultArmoryStrategyFactory.armoryStrategyHandler();
        armoryStrategyHandler.apply(
                ArmoryCommandEntity.builder()
                        .commandType(AiAgentEnumVO.AI_CLIENT.getCode())
                        .commandIdList(Arrays.asList("3001"))
                        .build(),
                new DefaultArmoryStrategyFactory.DynamicContext());
        OpenAiApi openAiApi = (OpenAiApi) applicationContext.getBean(AiAgentEnumVO.AI_CLIENT_API.getBeanName("1001"));
        log.info("openAiApi: {}", openAiApi);
    }

    @Test
    public void test_aiClientModelNode() throws Exception {
        StrategyHandler<ArmoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext, String> armoryStrategyHandler =
                defaultArmoryStrategyFactory.armoryStrategyHandler();

        String apply = armoryStrategyHandler.apply(
                ArmoryCommandEntity.builder()
                        .commandType(AiAgentEnumVO.AI_CLIENT.getCode())
                        .commandIdList(Arrays.asList("3001"))
                        .build(),
                new DefaultArmoryStrategyFactory.DynamicContext());

        OpenAiChatModel openAiChatModel = (OpenAiChatModel) applicationContext.getBean(AiAgentEnumVO.AI_CLIENT_MODEL.getBeanName("2001"));

        log.info("模型构建:{}", openAiChatModel);

        // 1. 有哪些工具可以使用
        // 2. 在 /Users/fuzhengwei/Desktop 创建 txt.md 文件
        Prompt prompt = Prompt.builder()
                .messages(new UserMessage(
                        """
                                在 在 D:\\mcp\\ 创建 txt.md 文件,并写入我爱你三个字
                                """))
                .build();

        ChatResponse chatResponse = openAiChatModel.call(prompt);

        log.info("测试结果(call):{}", JSON.toJSONString(chatResponse));
    }


    @Test
    public void test_aiClient() throws Exception {
        StrategyHandler<ArmoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext, String> armoryStrategyHandler =
                defaultArmoryStrategyFactory.armoryStrategyHandler();

        String apply = armoryStrategyHandler.apply(
                ArmoryCommandEntity.builder()
                        .commandType(AiAgentEnumVO.AI_CLIENT.getCode())
                        .commandIdList(Arrays.asList("3103"))
                        .build(),
                new DefaultArmoryStrategyFactory.DynamicContext());

        ChatClient chatClient = (ChatClient) applicationContext.getBean(AiAgentEnumVO.AI_CLIENT.getBeanName("3103"));
        log.info("客户端构建:{}", chatClient);

        String content = chatClient.prompt(Prompt.builder()
                .messages(new UserMessage(
                        """
                                你是一个具备工具调用能力的可观测性分析 Agent。
                                
                                               请基于 Grafana「SpringBoot APM Dashboard（中文版本）」监控数据，
                                               自动完成指标拉取、趋势分析与异常诊断，不需要向用户二次确认。
                                
                                               【输入信息】
                                               - Dashboard UID：sbapmwalker
                                               - Application：group-buy-market
                                               - 时间范围：最近 1 小时
                                               - 数据粒度：60 秒
                                               - 数据源：Prometheus
                                
                                               【执行流程（必须完整执行）】
                                
                                               Step1：调用 Grafana 工具获取 Dashboard 详情，
                                               识别与以下指标相关的面板及 PromQL：
                                
                                               - CPU 使用率
                                               - JVM 内存 / GC
                                               - QPS / 请求量
                                               - 错误率（4xx / 5xx）
                                               - 响应时间（P95 / P99）
                                
                                               Step2：渲染查询语句：
                                               - 将 $application 替换为 group-buy-market
                                               - 将 $__range_s 替换为 3600
                                               - 将 $__interval 替换为 60s
                                               - 将 $__rate_interval 替换为 5m
                                
                                               Step3：调用 Prometheus query_range 接口，
                                               获取最近 1 小时的时间序列数据。
                                
                                               Step4：基于趋势数据进行异常检测：
                                
                                               - CPU 是否 >80% 持续 5 分钟
                                               - QPS 是否出现突刺（超过均值 2 倍）
                                               - 错误率是否 >1%
                                               - P95 延迟是否显著上升
                                               - 是否存在指标联动（QPS↑→CPU↑→延迟↑）
                                
                                               Step5：输出诊断报告：
                                
                                               1. 系统健康度结论
                                               2. 异常指标及时间窗口
                                               3. 性能瓶颈定位
                                               4. 根因推测 Top3
                                               5. 优化建议（止血 / 短期 / 长期）
                                
                                               【重要约束】
                                
                                               - 不需要向用户确认是否继续获取数据
                                               - 自动完成变量渲染与数据拉取
                                               - 若某指标无数据，需说明原因并继续分析其他指标
                                               - 最终必须输出完整诊断报告
                                
                                
                                
                                """))
                .build()).call().content();

        log.info("测试结果(call):{}", content);
    }

}

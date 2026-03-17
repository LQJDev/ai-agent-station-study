package com.lqj.ai.domain.agent.service.armory;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqj.ai.domain.agent.model.entity.ArmoryCommandEntity;
import com.lqj.ai.domain.agent.model.valobj.enums.AiAgentEnumVO;
import com.lqj.ai.domain.agent.model.valobj.AiClientToolMcpVO;
import com.lqj.ai.domain.agent.service.armory.factory.DefaultArmoryStrategyFactory;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * @Author 李岐鉴
 * @Date 2025/9/25
 * @Description AiClientToolMcpNode 类
 */
@Slf4j
@Service
public class AiClientToolMcpNode extends AbstractArmorySupport {

    @Resource
    private AiClientModelNode aiClientModelNode;

    @Override
    protected String doApply(ArmoryCommandEntity requestParameter, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Ai agent 构建，工具MCP构建节点 {}", JSON.toJSONString(requestParameter));

        List<AiClientToolMcpVO> aiClientToolMcpList = dynamicContext.getValue(dataName());

        if (aiClientToolMcpList == null || aiClientToolMcpList.isEmpty()) {
            log.warn("Ai agent 构建，工具MCP构建节点，工具MCP列表为空");
            return router(requestParameter, dynamicContext);
        }
        for (AiClientToolMcpVO toolMcpVO : aiClientToolMcpList) {
            // 创建 MCP 对象
            McpSyncClient mcpSyncClient = createMcpSyncClient(toolMcpVO);
            // 注册 MCP 对象
            registerBean(beanName(toolMcpVO.getMcpId()), McpSyncClient.class, mcpSyncClient);
        }
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext, String> get(ArmoryCommandEntity armoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws Exception {
        return aiClientModelNode;
    }

    @Override
    protected String beanName(String beanId) {
        return AiAgentEnumVO.AI_CLIENT_TOOL_MCP.getBeanName(beanId);
    }


    @Override
    protected String dataName() {
        return AiAgentEnumVO.AI_CLIENT_TOOL_MCP.getDataName();
    }

    private McpSyncClient createMcpSyncClient(AiClientToolMcpVO aiClientToolMcpVO) {
        String transportType = aiClientToolMcpVO.getTransportType();

        switch (transportType) {
            case "sse" -> {
                AiClientToolMcpVO.TransportConfigSse transportConfigSse = aiClientToolMcpVO.getTransportConfigSse();
                // http://127.0.0.1:9999/sse/apiKey=1234567890
                String originalBaseUri = transportConfigSse.getBaseUri();
                String baseUri;
                String sseEndpoint;

                int queryParamStartIndex = originalBaseUri.indexOf("sse");
                if (queryParamStartIndex != -1) {
                    baseUri = originalBaseUri.substring(0, queryParamStartIndex - 1);
                    sseEndpoint = originalBaseUri.substring(queryParamStartIndex - 1);
                } else {
                    baseUri = originalBaseUri;
                    sseEndpoint = transportConfigSse.getSseEndpoint();
                }

                sseEndpoint = StringUtils.isBlank(sseEndpoint) ? "/sse" : sseEndpoint;
                HttpClientSseClientTransport sseClientTransport = HttpClientSseClientTransport
                        .builder(baseUri)
                        .sseEndpoint(sseEndpoint)
                        .build();
                McpSyncClient mcpSyncClient = McpClient.sync(sseClientTransport)
                        .requestTimeout(Duration.ofMinutes(aiClientToolMcpVO.getRequestTimeout())).build();
                var init_sse = mcpSyncClient.initialize();
                log.info("Tool SSE MCP Initialized {}", init_sse);
                log.info("SSE tools: {}", mcpSyncClient.listTools());
                return mcpSyncClient;
            }

            case "stdio" -> {
                AiClientToolMcpVO.TransportConfigStdio transportConfigStdio = aiClientToolMcpVO.getTransportConfigStdio();
                Map<String, AiClientToolMcpVO.TransportConfigStdio.Stdio> stdioMap = transportConfigStdio.getStdio();
                AiClientToolMcpVO.TransportConfigStdio.Stdio stdio = stdioMap.get(aiClientToolMcpVO.getMcpName());

                ServerParameters stdioParams = ServerParameters.builder(stdio.getCommand())
                        .args(stdio.getArgs())
                        .env(stdio.getEnv())
                        .build();
                ObjectMapper objectMapper = new ObjectMapper();
                McpSyncClient mcpClient = McpClient.sync(new StdioClientTransport(stdioParams, McpJsonMapper.getDefault()))
                        .requestTimeout(Duration.ofMinutes(aiClientToolMcpVO.getRequestTimeout()))
                        .build();

                var init_stdio = mcpClient.initialize();
                log.info("Tool STDIO MCP Initialized {}", init_stdio);
                return mcpClient;
            }

            case "streamable-http" -> {
                AiClientToolMcpVO.TransportConfigStreamableHttp config =
                        aiClientToolMcpVO.getTransportConfigStreamableHttp();

                if (config == null) {
                    throw new IllegalArgumentException("streamable-http transportConfig 不能为空");
                }
                String endpoint = config.getEndpoint();
                if (StringUtils.isBlank(endpoint)) {
                    throw new IllegalArgumentException("streamable-http endpoint 不能为空");
                }

                // 例如: http://127.0.0.1:8080/mcp
                HttpClientStreamableHttpTransport streamableTransport =
                        HttpClientStreamableHttpTransport.builder(endpoint)
                                .build();

                McpSyncClient mcpSyncClient = McpClient.sync(streamableTransport)
                        .requestTimeout(Duration.ofMinutes(aiClientToolMcpVO.getRequestTimeout()))
                        .build();

                var initHttp = mcpSyncClient.initialize();
                log.info("Tool Streamable HTTP MCP Initialized {}", initHttp);
                log.info("Streamable HTTP tools: {}", mcpSyncClient.listTools());
                return mcpSyncClient;
            }

        }
        throw new RuntimeException("err! transportType" + transportType + " not exist!");

    }

}

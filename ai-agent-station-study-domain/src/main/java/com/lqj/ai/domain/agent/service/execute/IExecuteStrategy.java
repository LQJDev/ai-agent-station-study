package com.lqj.ai.domain.agent.service.execute;

import com.lqj.ai.domain.agent.model.entity.ExecuteCommandEntity;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * @Author 李岐鉴
 * @Date 2025/10/6
 * @Description 执行策略接口
 */
public interface IExecuteStrategy {

    void execute(ExecuteCommandEntity requestParameter, ResponseBodyEmitter emitter) throws Exception;
}

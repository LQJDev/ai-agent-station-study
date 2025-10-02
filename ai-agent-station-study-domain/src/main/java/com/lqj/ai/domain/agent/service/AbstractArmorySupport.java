package com.lqj.ai.domain.agent.service;


import cn.bugstack.wrench.design.framework.tree.AbstractMultiThreadStrategyRouter;
import com.lqj.ai.domain.agent.adapter.repository.IAgentRepository;
import com.lqj.ai.domain.agent.model.entity.ArmoryCommandEntity;
import com.lqj.ai.domain.agent.service.armory.factory.DefaultArmoryStrategyFactory;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeoutException;

/**
 * 装配支撑类通用骨架（依赖注入、多线程入口、上下文传递）
 *
 * @author 李岐鉴
 * 2025/6/27 07:14
 */
public abstract class AbstractArmorySupport extends AbstractMultiThreadStrategyRouter<ArmoryCommandEntity, DefaultArmoryStrategyFactory.DynamicContext, String> {

    private final Logger log = LoggerFactory.getLogger(AbstractArmorySupport.class);

    @Resource
    protected ApplicationContext applicationContext;

    @Resource
    protected ThreadPoolExecutor threadPoolExecutor;

    @Resource
    protected IAgentRepository repository;

    @Override
    protected void multiThread(ArmoryCommandEntity requestParameter, DefaultArmoryStrategyFactory.DynamicContext dynamicContext) throws ExecutionException, InterruptedException, TimeoutException {
        // 缺省的
    }

    protected String beanName(String id) {
        return null;
    }

    protected String dataName() {
        return null;
    }

    /**
     * 通用的注册Bean方法
     * @param beanName
     * @param beanClass
     * @param beanInstance
     */
    protected synchronized <T> void registerBean(String beanName, Class<T> beanClass, T beanInstance) {
        DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        // 注册Bean
        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(beanClass, () -> beanInstance);
        AbstractBeanDefinition beanDefinition = beanDefinitionBuilder.getRawBeanDefinition();
        beanDefinition.setScope(BeanDefinition.SCOPE_SINGLETON);
        // 如果Bean已存在，先移除
        if (beanFactory.containsBean(beanName)) {
            beanFactory.removeBeanDefinition(beanName);
        }
        // 注册新的Bean
        beanFactory.registerBeanDefinition(beanName, beanDefinition);
        log.info("成功注册Bean: {}", beanName);
    }

    protected <T> T getBean(String beanName) {
        return (T) applicationContext.getBean(beanName);
    }

}

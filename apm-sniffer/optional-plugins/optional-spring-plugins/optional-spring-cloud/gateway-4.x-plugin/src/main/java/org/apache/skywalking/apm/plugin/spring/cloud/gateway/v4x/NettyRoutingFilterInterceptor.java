/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.apm.plugin.spring.cloud.gateway.v4x;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;

import java.lang.reflect.Method;

import static org.apache.skywalking.apm.network.trace.component.ComponentsDefine.SPRING_CLOUD_GATEWAY;

/**
 * This class intercept <code>filter</code> method.
 * <p>
 * <code>spring-webflux-5.x-plugin</code> will inject context snapshot into skywalking dynamic field, and this
 * interceptor will continue the span in another thread.
 * </p>
 *
 * <pre>
 * (这个类拦截 filter()。
 * spring-webflux-5.x-plugin 会将 上下文快照 注入 skywalking 动态域 中，这个 拦截器 会在另一个线程中继续执行span。)
 *
 * 增强类：org.springframework.cloud.gateway.filter.NettyRoutingFilter
 * 增强方法：（重写了方法参数）Mono≤Void> filter(ServerWebExchange exchange, GatewayFilterChain chain)
 * </pre>
 */
public class NettyRoutingFilterInterceptor implements InstanceMethodsAroundInterceptor {
    private static final String NETTY_ROUTING_FILTER_TRACED_ATTR = NettyRoutingFilterInterceptor.class.getName() + ".isTraced";

    /**
     * @param objInst gateway.filter.NettyRoutingFilter
     * @param method filter
     * @param allArguments [exchange, chain]
     * @param argumentsTypes [ServerWebExchange, GatewayFilterChain]
     */
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            MethodInterceptResult result) throws Throwable {
        ServerWebExchange exchange = (ServerWebExchange) allArguments[0];
        // 该 exchange 已被标记为 被追踪，直接返回
        if (isTraced(exchange)) {
            return;
        }

        // 标记 exchange 为 被追踪
        setTracedStatus(exchange);

        // 得到 EnhancedInstance 类型的 exchange，或者 null QFTODO：exchange 在哪里被重写的？
        EnhancedInstance enhancedInstance = getInstance(allArguments[0]);

        // 创建 操作名为 SpringCloudGateway/RoutingFilter 的 local span
        AbstractSpan span = ContextManager.createLocalSpan("SpringCloudGateway/RoutingFilter");
        // 如果 方法参数 exchange 被增强过，且 动态增强域 的值不为空
        if (enhancedInstance != null && enhancedInstance.getSkyWalkingDynamicField() != null) {
            // 从 enhancedInstance 获取 Trace上下文快照 并执行 continued 继续
            ContextManager.continued((ContextSnapshot) enhancedInstance.getSkyWalkingDynamicField());
        }
        // 设置 span 的 组件为 spring-cloud-gateway
        span.setComponent(SPRING_CLOUD_GATEWAY);
    }

    /** 通过 ServerWebExchange 的属性，标记 ServerWebExchange 为 被追踪 */
    private static void setTracedStatus(ServerWebExchange exchange) {
        exchange.getAttributes().put(NETTY_ROUTING_FILTER_TRACED_ATTR, true);
    }

    /** true: ServerWebExchange 的熟悉中 包含 被追踪的标记 */
    private static boolean isTraced(ServerWebExchange exchange) {
        return exchange.getAttributeOrDefault(NETTY_ROUTING_FILTER_TRACED_ATTR, false);
    }

    /** 得到 EnhancedInstance 类型的 o（o 也就是 ServerWebExchange），或者 null */
    private EnhancedInstance getInstance(Object o) {
        EnhancedInstance instance = null;
        // 如果 ServerWebExchange 是被增强了的，则转换为 EnhancedInstance 类型
        if (o instanceof EnhancedInstance) {
            instance = (EnhancedInstance) o;
        } else if (o instanceof ServerWebExchangeDecorator) {
            // 如果 ServerWebExchange 是 ServerWebExchangeDecorator 类型，则获取它的 委托类，再递归执行 getInstance()，以提取 EnhancedInstance 类型的 ServerWebExchange
            ServerWebExchange delegate = ((ServerWebExchangeDecorator) o).getDelegate();
            return getInstance(delegate);
        }
        // 返回 EnhancedInstance 类型的 ServerWebExchange
        return instance;
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            Object ret) throws Throwable {
        if (ContextManager.isActive()) {
            // if HttpClientFinalizerSendInterceptor does not invoke, we will stop the span there to avoid context leak.
            // (如果 HttpClientFinalizerSendInterceptor 没有调用，我们将在那里停止 span 以避免上下文泄漏。)
            // 停止 当前活跃的 Span
            ContextManager.stopSpan();
        }
        return ret;
    }

    /** filter() 执行失败 的 异常处理 */
    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
            Class<?>[] argumentsTypes, Throwable t) {
        // 设置 active span 的 log 为 t
        ContextManager.activeSpan().log(t);
    }
}

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

import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.plugin.spring.cloud.gateway.v4x.define.EnhanceObjectCache;
import org.apache.skywalking.apm.util.StringUtil;
import org.reactivestreams.Publisher;
import reactor.netty.NettyOutbound;
import reactor.netty.http.client.HttpClientRequest;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.function.BiFunction;

import static org.apache.skywalking.apm.network.trace.component.ComponentsDefine.SPRING_CLOUD_GATEWAY;

/**
 * This class intercept <code>send</code> method.
 * <p>
 * In before method, create a new BiFunction lambda expression for setting <code>ContextCarrier</code> to http header
 * and replace the original lambda in argument
 *
 * <pre>
 * (此类拦截 send 方法。
 * 在 before 方法中，创建一个新的 BiFunction lambda 表达式，用于将 ContextCarrier 设置为 http头，并替换参数中的原始 lambda)
 *
 * 增强类：reactor.netty.http.client.HttpClientFinalizer（v1.1.0）
 * 增强方法2：（重写了方法参数）HttpClientFinalizer send(BiFunction≤? super HttpClientRequest, ? super NettyOutbound, ? extends Publisher≤Void>> sender)
 * </pre>
 */
public class HttpClientFinalizerSendInterceptor implements InstanceMethodsAroundInterceptor {

    /**
     * @param objInst HttpClientFinalizer 的增强了的对象
     * @param method send()
     * @param allArguments [BiFunction]
     * @param argumentsTypes [sender]
     * @param result change this result, if you want to truncate the method.
     * @throws Throwable
     */
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            MethodInterceptResult result) throws Throwable {
        // objInst 的 动态增强域 在 HttpClientFinalizerConstructorInterceptor.onConstruct() 中 被设置了
        EnhanceObjectCache enhanceObjectCache = (EnhanceObjectCache) objInst.getSkyWalkingDynamicField();
        if (enhanceObjectCache == null) {
            return;
        }
        
        /*
          In this plug-in, the HttpClientFinalizerSendInterceptor depends on the NettyRoutingFilterInterceptor
          When the NettyRoutingFilterInterceptor is not executed, the HttpClientFinalizerSendInterceptor has no meaning to be executed independently
          and using ContextManager.activeSpan() method would cause NPE as active span does not exist.
         */
        if (!ContextManager.isActive()) {
            return;
        }

        // 获取当前活跃的 Span
        AbstractSpan span = ContextManager.activeSpan();
        // 准备异步 span
        span.prepareForAsync();

        // 如果 objInst 的 动态增强域 的 url 不为空
        if (StringUtil.isNotEmpty(enhanceObjectCache.getUrl())) {
            URL url = new URL(enhanceObjectCache.getUrl());

            // 创建 跨进程传输载体
            ContextCarrier contextCarrier = new ContextCarrier();
            // 创建 exit span，并将 当前的 TracerContext 和 该 exit span 的相关信息 注入 到 ContextCarrier 中
            AbstractSpan abstractSpan = ContextManager.createExitSpan(
                    "SpringCloudGateway/sendRequest", contextCarrier, getPeer(url));
            // 设置 span 的 URL 标签
            Tags.URL.set(abstractSpan, enhanceObjectCache.getUrl());
            // 准备异步 span，异步 span 在 当前 跟踪上下文 结束（但 该异步span 仍然是活动的，直到 该异步span.asyncFinish 调用（HttpClientFinalizerResponseConnectionInterceptor.afterMethod()））
            abstractSpan.prepareForAsync();
            // 设置 span 的 组件为 spring-cloud-gateway
            abstractSpan.setComponent(SPRING_CLOUD_GATEWAY);
            // 设置 span 层为 HTTP
            abstractSpan.setLayer(SpanLayer.HTTP);
            // 异步 span 在 当前 跟踪上下文 结束（但 该异步span 仍然是活动的，直到 该异步span.asyncFinish 调用（HttpClientFinalizerResponseConnectionInterceptor.afterMethod()））
            ContextManager.stopSpan(abstractSpan);

            // 创建新的 BiFunction lambda 表达式，用于将 ContextCarrier 设置为 http头，并替换参数中的原始 lambda
            BiFunction<? super HttpClientRequest, ? super NettyOutbound, ? extends Publisher<Void>> finalSender = (BiFunction<? super HttpClientRequest, ? super NettyOutbound, ? extends Publisher<Void>>) allArguments[0];
            // 替换 参数1 的原始 lambda
            allArguments[0] = (BiFunction<HttpClientRequest, NettyOutbound, Publisher<Void>>) (request, outbound) -> {
                // 执行 原始参数1的 lambda
                Publisher publisher = finalSender.apply(request, outbound);

                // 将 上下文载体 的 key-value 设置到 request 的 headers 中
                CarrierItem next = contextCarrier.items();
                while (next.hasNext()) {
                    next = next.next();
                    request.requestHeaders().remove(next.getHeadKey());
                    request.requestHeaders().set(next.getHeadKey(), next.getHeadValue());
                }
                // 返回 原始参数1的 lambda 的执行结果
                return publisher;
            };
            // 将 异步span 设置到 objInst 的 动态增强域 的 span 域
            enhanceObjectCache.setCacheSpan(abstractSpan);
        }
        // 异步 span 在 当前 跟踪上下文 结束（但 该异步span 仍然是活动的，直到 该异步span.asyncFinish 调用（HttpClientFinalizerResponseConnectionInterceptor.afterMethod()））
        ContextManager.stopSpan(span);
        // 将 异步span 设置到 objInst 的 动态增强域 的 span1 域
        enhanceObjectCache.setSpan1(span);
    }

    private String getPeer(URL url) {
        return url.getHost() + ":" + url.getPort();
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            Object ret) {
        // 将 objInst 的 动态增强域 EnhanceObjectCache对象 设置到 ret 的 动态增强域
        ((EnhancedInstance) ret).setSkyWalkingDynamicField(objInst.getSkyWalkingDynamicField());
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
            Class<?>[] argumentsTypes, Throwable t) {

    }
}

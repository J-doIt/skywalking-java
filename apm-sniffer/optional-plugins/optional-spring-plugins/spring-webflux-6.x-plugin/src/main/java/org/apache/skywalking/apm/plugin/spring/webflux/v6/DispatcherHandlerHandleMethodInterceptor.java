/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.plugin.spring.webflux.v6;

import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.tag.Tags.HTTP;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;
import org.springframework.web.server.adapter.DefaultServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.List;

/**
 * <pre>
 * 增强类：org.springframework.web.reactive.DispatcherHandler
 * 增强方法：
 *          Mono≤Void> handle(ServerWebExchange exchange)
 * </pre>
 */
public class DispatcherHandlerHandleMethodInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        EnhancedInstance instance = getInstance(allArguments[0]);

        ServerWebExchange exchange = (ServerWebExchange) allArguments[0];

        ContextCarrier carrier = new ContextCarrier();
        CarrierItem next = carrier.items();
        HttpHeaders headers = exchange.getRequest().getHeaders();
        // 将 exchange.request.headers 中的 链路信息 放到 carrier 中
        /* （上游的链路信息） */
        while (next.hasNext()) {
            next = next.next();
            List<String> header = headers.get(next.getHeadKey());
            if (header != null && header.size() > 0) {
                next.setHeadValue(header.get(0));
            }
        }

        // 创建 entry span ，并从 carrier 中 提取 追踪信息 到 当前tracingContext
        AbstractSpan span = ContextManager.createEntrySpan(exchange.getRequest().getURI().getPath(), carrier);

        // 如果 var1（ServerWebExchange）的 增强域 不为空（QFTODO：说明该 handler 实例方法不是第一次执行了？）
        if (instance != null && instance.getSkyWalkingDynamicField() != null) {
            // 从 var1（ServerWebExchange）的增强域拿到 tracing上下文快照，并 ref 到 当前tracingContext
            /* QFTODO：为什么还要从 var1（ServerWebExchange）的 增强域 获取 上下文快照 */
            ContextManager.continued((ContextSnapshot) instance.getSkyWalkingDynamicField());
        }
        span.setComponent(ComponentsDefine.SPRING_WEBFLUX);
        SpanLayer.asHttp(span);
        Tags.URL.set(span, exchange.getRequest().getURI().toString());
        HTTP.METHOD.set(span, exchange.getRequest().getMethod().name());
        // 将 var1（ServerWebExchange） 的增强域 设置为 new的当前tracing上下文快照
        /* QFTODO：为什么还要设置 上下文快照 到 var1（ServerWebExchange）的 增强域 */
        instance.setSkyWalkingDynamicField(ContextManager.capture());
        // 该span准备进入异步模式
        span.prepareForAsync();
        // 结束 active span
        ContextManager.stopSpan(span);

        // 将 该span 放入 var1（ServerWebExchange）的 attributes 中
        exchange.getAttributes().put("SKYWALKING_SPAN", span);
    }

    /**
     * 如果 ServerWebExchange.Attribute中的最佳匹配路径模式 与 请求中的url 匹配，则设置 span 的 OperationName。
     * @param span
     * @param exchange
     */
    private void maybeSetPattern(AbstractSpan span, ServerWebExchange exchange) {
        if (span != null) {
            // 从 ServerWebExchange 的 Attribute 中获取 最佳匹配路径模式
            PathPattern pathPattern = exchange.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            //check if the best matching pattern matches url in request
            //to avoid operationName overwritten by fallback url
            // 检查最佳匹配路径模式是否与请求中的 URL 匹配，以避免 操作名 称被 回退URL 覆盖
            if (pathPattern != null && pathPattern.matches(exchange.getRequest().getPath().pathWithinApplication())) {
                span.setOperationName(pathPattern.getPatternString());
            }
        }

    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {

        ServerWebExchange exchange = (ServerWebExchange) allArguments[0];

        // 从 var1（ServerWebExchange）的 attributes 中拿到 span（QFTODO：）
        AbstractSpan span = (AbstractSpan) exchange.getAttributes().get("SKYWALKING_SPAN");
        Mono<Void> monoReturn = (Mono<Void>) ret;

        // add skywalking context snapshot to reactor context.
        EnhancedInstance instance = getInstance(allArguments[0]);
        // 从 var1（ServerWebExchange）的增强域 取值（当前tracing上下文快照）（QFTODO：）
        if (instance != null && instance.getSkyWalkingDynamicField() != null) {
            // 定义 MonoContextWrite 的 doOnContext 函数
            monoReturn = monoReturn.contextWrite(
                    // 将 当前tracing上下文快照 添加到 Reactor 上下文中（QFTODO：）
                c -> c.put("SKYWALKING_CONTEXT_SNAPSHOT", instance.getSkyWalkingDynamicField()));
        }

        // 定义 ret（Mono<Void>） 的 doFinally 函数：在 Finally 时处理Span
        return monoReturn.doFinally(s -> {

            // 异步线程中执行

            if (span != null) {
                // 如果url匹配，则设置 span 的 OperationName
                maybeSetPattern(span, exchange);
                try {

                    HttpStatusCode httpStatus = exchange.getResponse().getStatusCode();
                    // fix webflux-2.0.0-2.1.0 version have bug. httpStatus is null. not support
                    // （修复 webflux-2.0.0-2.1.0 版本中的 bug，httpStatus 可能为 null）
                    if (httpStatus != null) {
                        // 为 span 设置 http.status_code 标签
                        Tags.HTTP_RESPONSE_STATUS_CODE.set(span, httpStatus.value());
                        // 如果是错误响应，标记 Span 为错误
                        if (httpStatus.isError()) {
                            span.errorOccurred();
                        }
                    }
                } finally {
                    // 异步结束 span
                    span.asyncFinish();
                }
            }
        });
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
    }

    public static EnhancedInstance getInstance(Object o) {
        EnhancedInstance instance = null;
        if (o instanceof DefaultServerWebExchange) {
            instance = (EnhancedInstance) o;
        } else if (o instanceof ServerWebExchangeDecorator) {
            ServerWebExchange delegate = ((ServerWebExchangeDecorator) o).getDelegate();
            return getInstance(delegate);
        }
        return instance;
    }

}

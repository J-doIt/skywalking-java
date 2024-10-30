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

package org.apache.skywalking.apm.plugin.spring.mvc.v5;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.RuntimeContext;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2.InstanceMethodsAroundInterceptorV2;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2.MethodInvocationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;

import static org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants.REACTIVE_ASYNC_SPAN_IN_RUNTIME_CONTEXT;
import static org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants.REQUEST_KEY_IN_RUNTIME_CONTEXT;
import static org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants.RESPONSE_KEY_IN_RUNTIME_CONTEXT;

/**
 *<pre>
 * 增强类 webflux 的 InvocableHandlerMethod
 * 增强方法：Mono≤HandlerResult> invoke(ServerWebExchange exchange, BindingContext bindingContext, Object... providedArgs)
 * </pre>
 */
public class InvokeInterceptor implements InstanceMethodsAroundInterceptorV2 {

    /**
     * @param objInst InvocableHandlerMethod 的增强后的实例
     * @param method invoke
     * @param allArguments
     * @param argumentsTypes
     * @param context the method invocation context including result context.
     * @throws Throwable
     */
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInvocationContext context) throws Throwable {
        // 获取 ServerWebExchange 对象
        ServerWebExchange exchange = (ServerWebExchange) allArguments[0];
        // 获取响应对象
        final ServerHttpResponse response = exchange.getResponse();
        /**
         * First, we put the slot in the RuntimeContext,
         * as well as the MethodInvocationContext (MIC),
         * so that we can access it in the {@link org.apache.skywalking.apm.plugin.spring.mvc.v5.InvokeInterceptor#afterMethod}
         * Then we fetch the slot from {@link org.apache.skywalking.apm.plugin.spring.mvc.commons.interceptor.AbstractMethodInterceptor#afterMethod}
         * and fulfill the slot with the real AbstractSpan.
         * Afterwards, we can safely remove the RuntimeContext.
         * Finally, when the lambda executes in the {@link reactor.core.publisher.Mono#doFinally},
         * ref of span could be acquired from MIC.
         * （
         * 首先，我们将 slot 放入 RuntimeContext 中，以及放入 MethodInvocationContext (MIC) 中，
         *      这样我们可以在 v5.InvokeInterceptor.afterMethod() 中访问它。
         * 然后，我们在 AbstractMethodInterceptor.afterMethod() 中获取 slot，并用实际的 AbstractSpan 填充它。
         * 之后，我们可以安全地移除 RuntimeContext。
         * 最后，当 lambda 在 Mono.doFinally() 中执行时，可以从 MIC 中获取 span 的引用。）
         */
        AbstractSpan[] ref = new AbstractSpan[1];
        RuntimeContext runtimeContext = ContextManager.getRuntimeContext();
        // 为 当前追踪上下文 的 runtimeContext 放置 值
        runtimeContext.put(REACTIVE_ASYNC_SPAN_IN_RUNTIME_CONTEXT, ref);
        runtimeContext.put(RESPONSE_KEY_IN_RUNTIME_CONTEXT, response);
        runtimeContext.put(REQUEST_KEY_IN_RUNTIME_CONTEXT, exchange.getRequest());
        // 将 span引用数组 放入 MethodInvocationContext 中
        context.setContext(ref);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret, MethodInvocationContext context) throws Throwable {
        ServerWebExchange exchange = (ServerWebExchange) allArguments[0];
        // 返回一个 Mono 对象，并在 doFinally 中执行清理操作
        return ((Mono) ret).doFinally(s -> {
            // 从 MethodInvocationContext 中获取上下文
            Object ctx = context.getContext();
            if (ctx == null) {
                return;
            }
            // 从上下文中获取 AbstractSpan 数组，并取出 span
            AbstractSpan span = ((AbstractSpan[]) ctx)[0];
            if (span == null) {
                return;
            }
            // 获取响应的状态码
            HttpStatus httpStatus = exchange.getResponse().getStatusCode();
            if (httpStatus != null) {
                // 设置响应状态码到 span 中
                Tags.HTTP_RESPONSE_STATUS_CODE.set(span, httpStatus.value());
                if (httpStatus.isError()) {
                    // 如果状态码表示错误（4xx 或 5xx），标记 span 为错误
                    span.errorOccurred();
                }
            }
            // 完成 异步 span
            span.asyncFinish();
        });
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t, MethodInvocationContext context) {

    }
}

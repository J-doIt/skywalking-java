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

package org.apache.skywalking.apm.plugin.spring.webflux.v6.webclient;

import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2.InstanceMethodsAroundInterceptorV2;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2.MethodInvocationContext;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Optional;

/**
 * <pre>
 * 增强类：org.springframework.web.reactive.function.client.ExchangeFunctions$DefaultExchangeFunction
 * 增强方法：
 *          Mono≤ClientResponse> exchange(ClientRequest clientRequest)
 * </pre>
 */
public class WebFluxWebClientInterceptor implements InstanceMethodsAroundInterceptorV2 {

    @Override
    public void beforeMethod(EnhancedInstance objInst,
                             Method method,
                             Object[] allArguments,
                             Class<?>[] argumentsTypes,
                             MethodInvocationContext context) throws Throwable {

    }

    @Override
    public Object afterMethod(EnhancedInstance objInst,
                              Method method,
                              Object[] allArguments,
                              Class<?>[] argumentsTypes,
                              Object ret,
                              MethodInvocationContext context) throws Throwable {
        // fix the problem that allArgument[0] may be null
        if (allArguments[0] == null) {
            return ret;
        }
        Mono<ClientResponse> ret1 = (Mono<ClientResponse>) ret;
        // 定义一个 参数为 Function<ContextView, ? extends Mono<? extends T>> 的 Mono提供者，并返回
        return Mono.deferContextual(ctx/* Function<ContextView, Mono<T>> */ -> {
            ClientRequest request = (ClientRequest) allArguments[0];
            URI uri = request.url();
            final String operationName = getRequestURIString(uri);
            final String remotePeer = getIPAndPort(uri);
            // 创建 exit span
            AbstractSpan span = ContextManager.createExitSpan(operationName, remotePeer);

            // get ContextSnapshot from reactor context,  the snapshot is set to reactor context by any other plugin
            // such as DispatcherHandlerHandleMethodInterceptor in spring-webflux-5.x-plugin
            // QFTODO："SKYWALKING_CONTEXT_SNAPSHOT" 是在 spring-webflux-x.x-plugin 的 DispatcherHandlerHandleMethodInterceptor.handle 的 afterMethod 时设置的
            final Optional<Object> optional = ctx.getOrEmpty("SKYWALKING_CONTEXT_SNAPSHOT");
            // 如果 ctx的 ContextView 中 存在 上下文快照，则 执行 continued 继续
            optional.ifPresent(snapshot -> ContextManager.continued((ContextSnapshot) snapshot));

            //set components name
            span.setComponent(ComponentsDefine.SPRING_WEBCLIENT);
            Tags.URL.set(span, uri.toString());
            Tags.HTTP.METHOD.set(span, request.method().toString());
            SpanLayer.asHttp(span);

            final ContextCarrier contextCarrier = new ContextCarrier();
            // 将 当前tracingContext 注入到 new 的 上下文载体（ContextCarrier）
            ContextManager.inject(contextCarrier);
            if (request instanceof EnhancedInstance) {
                // 将 ClientRequest 的 增强域 的值 设置为 new 的上下文载体（ContextCarrier）
                ((EnhancedInstance) request).setSkyWalkingDynamicField(contextCarrier);
            }

            //user async interface
            // 该span准备进入异步模式
            span.prepareForAsync();
            // 结束 active span
            ContextManager.stopSpan();

            // 定义 ret1（Mono<ClientResponse>） 的 doOnSuccess 函数：在 Success 时 处理Span
            // 定义 ret1（Mono<ClientResponse>） 的 doOnError 函数：在 Error 时 结束 span
            return ret1.doOnSuccess(clientResponse -> {
                HttpStatusCode httpStatus = clientResponse.statusCode();
                if (httpStatus != null) {
                    // 设置 span 的 http.status_code 标签
                    Tags.HTTP_RESPONSE_STATUS_CODE.set(span, httpStatus.value());
                    if (httpStatus.isError()) {
                        span.errorOccurred();
                    }
                }
            }).doOnError(span::log).doFinally(s -> {
                // 异步结束 span
                span.asyncFinish();
            });
        });
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst,
                                      Method method,
                                      Object[] allArguments,
                                      Class<?>[] argumentsTypes,
                                      Throwable t,
                                      MethodInvocationContext context) {
        AbstractSpan activeSpan = ContextManager.activeSpan();
        activeSpan.errorOccurred();
        activeSpan.log(t);
    }

    private String getRequestURIString(URI uri) {
        String requestPath = uri.getPath();
        return requestPath != null && requestPath.length() > 0 ? requestPath : "/";
    }

    // return ip:port
    private String getIPAndPort(URI uri) {
        return uri.getHost() + ":" + uri.getPort();
    }
}

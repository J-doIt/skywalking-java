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

import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.plugin.spring.cloud.gateway.v4x.define.EnhanceObjectCache;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClientResponse;

import java.lang.reflect.Method;
import java.util.function.BiFunction;

/**
 * This class intercept <code>responseConnection</code> method.
 * <p>
 * After downstream service response, finish the span in the {@link EnhanceObjectCache}.
 *
 * <pre>
 * (此类拦截 responseConnection 方法。
 * 在 下游服务 响应后，在 EnhanceObjectCache 中完成 span。)
 *
 * 增强类：reactor.netty.http.client.HttpClientFinalizer（v1.1.0）
 * 增强方法：（重写了方法参数）≤V> Flux≤V> responseConnection(BiFunction≤? super HttpClientResponse, ? super Connection, ? extends Publisher≤V>> receiver)
 * </pre>
 */
public class HttpClientFinalizerResponseConnectionInterceptor implements InstanceMethodsAroundInterceptor {

    /**
     *
     * @param objInst HttpClientFinalizer 的增强了的对象
     * @param method responseConnection()
     * @param allArguments [receiver]
     * @param argumentsTypes [BiFunction]
     * @param result change this result, if you want to truncate the method.
     */
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            MethodInterceptResult result) {
        // 参数1 的原始 lambda
        BiFunction<? super HttpClientResponse, ? super Connection, ? extends Publisher> finalReceiver = (BiFunction<? super HttpClientResponse, ? super Connection, ? extends Publisher>) allArguments[0];

        // 获取 objInst 的 动态增强域
        EnhanceObjectCache cache = (EnhanceObjectCache) objInst.getSkyWalkingDynamicField();
        // 替换 参数1 的原始 lambda
        allArguments[0] = (BiFunction<HttpClientResponse, Connection, Publisher>) (response, connection) -> {
            // 执行 原始参数1的 lambda
            Publisher publisher = finalReceiver.apply(response, connection);
            if (cache == null) {
                return publisher;
            }
            // receive the response.
            if (cache.getSpan() != null) {
                if (response.status().code() >= HttpResponseStatus.BAD_REQUEST.code()) {
                    // 标记 span 为错误
                    cache.getSpan().errorOccurred();
                }
                // 设置 response 的 状态码到 span 的 tag
                Tags.HTTP_RESPONSE_STATUS_CODE.set(cache.getSpan(), response.status().code());
            }
            
            return publisher;
        };
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) {
        Flux<?> responseFlux = (Flux<?>) ret;

        responseFlux = responseFlux
                // 接收应答失败时调用
                .doOnError(e -> {
                    EnhanceObjectCache cache = (EnhanceObjectCache) objInst.getSkyWalkingDynamicField();
                    if (cache == null) {
                        return;
                    }
                    
                    if (cache.getSpan() != null) {
                        // 标记 span 为错误
                        cache.getSpan().errorOccurred();
                        // 设置 span 的 log 为 e
                        cache.getSpan().log(e);
                    }
                })
                // 接收应答完成后调用
                .doFinally(signalType -> {
                    EnhanceObjectCache cache = (EnhanceObjectCache) objInst.getSkyWalkingDynamicField();
                    if (cache == null) {
                        return;
                    }
                    // do finally. Finish the span.
                    if (cache.getSpan() != null) {
                        if (signalType == SignalType.CANCEL) {
                            // 标记 span 为错误
                            cache.getSpan().errorOccurred();
                        }
                        // 完成 异步 span
                        cache.getSpan().asyncFinish();
                    }

                    if (cache.getSpan1() != null) {
                        // 完成 异步 span
                        cache.getSpan1().asyncFinish();
                    }
                });

        return responseFlux;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
            Class<?>[] argumentsTypes, Throwable t) {

    }
}

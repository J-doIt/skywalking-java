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

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.plugin.spring.cloud.gateway.v4x.define.EnhanceObjectCache;

import java.lang.reflect.Method;

/**
 * This class intercept <code>uri</code> method to get the url of downstream service
 *
 * <pre>
 * (这个类拦截 uri() 获取 下游服务 的 url。)
 *
 * 增强类：reactor.netty.http.client.HttpClientFinalizer（v1.1.0）
 * 增强方法：名为 uri 的方法（有3个）
 *              HttpClient.RequestSender uri(Mono≤String> uri)
 *              HttpClient.RequestSender uri(String uri)
 *              HttpClient.RequestSender uri(URI uri)
 * </pre>
 */
public class HttpClientFinalizerUriInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            MethodInterceptResult result) throws Throwable {
    }

    /**
     * @param objInst HttpClientFinalizer
     * @param method uri()
     * @param allArguments uri
     * @param argumentsTypes  Mono≤String> 或 String 或 URI
     * @param ret HttpClient.RequestSender 对象（QFTODO：在哪里被增强的？）
     */
    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            Object ret) throws Throwable {
        // 如果 ret 是被增强了的，则获取 ret 的 动态增强域 enhanceObjectCache
        if (ret instanceof EnhancedInstance) {
            EnhanceObjectCache enhanceObjectCache = (EnhanceObjectCache) ((EnhancedInstance) ret)
                    .getSkyWalkingDynamicField();
            // 设置 动态增强域 的 url
            enhanceObjectCache.setUrl(String.valueOf(allArguments[0]));
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
            Class<?>[] argumentsTypes, Throwable t) {
    }
}

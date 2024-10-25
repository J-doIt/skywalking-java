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

package org.apache.skywalking.apm.plugin.okhttp.common;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import java.lang.reflect.Method;

/**
 * {@link OnResponseInterceptor} validate the response code if it is great equal than 400. if so. the transaction status
 * chang to `error`, or do nothing.
 *
 * <pre>
 * （OnResponseInterceptor 在 大于400 时验证响应代码。如果是这样的话。事务状态变为“error”，或者什么都不做。）
 *
 * 增强类 okhttp3.Callback 及其子类
 * 增强方法：fun onResponse(call: Call, response: Response)
 * </pre>
 */
public class OnResponseInterceptor implements InstanceMethodsAroundInterceptor {

    /**
     * @param objInst Callback
     * @param method fun onResponse(call: Call, response: Response)
     * @param allArguments [ Call 的实例, Response 的实例 ]
     * @param argumentsTypes [ Call.class, Response.class ]
     * @param result change this result, if you want to truncate the method.
     * @throws Throwable
     */
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        // 创建 操作名 为 Callback/onResponse 的 local span
        ContextManager.createLocalSpan("Callback/onResponse");
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        // stop span
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        // 设置 active span 的 log 为 Throwable
        ContextManager.activeSpan().log(t);
    }
}

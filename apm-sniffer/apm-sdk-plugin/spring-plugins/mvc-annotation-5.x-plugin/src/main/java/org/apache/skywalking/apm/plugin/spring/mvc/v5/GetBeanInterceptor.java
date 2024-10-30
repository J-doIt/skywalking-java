/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.apm.plugin.spring.mvc.v5;

import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants.REQUEST_KEY_IN_RUNTIME_CONTEXT;
import static org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants.RESPONSE_KEY_IN_RUNTIME_CONTEXT;

/**
 * {@link GetBeanInterceptor} pass the {@link NativeWebRequest} object into the {@link
 * org.springframework.stereotype.Controller} object.
 *
 * <pre>
 * (GetBeanInterceptor 将 NativeWebRequest对象 传递到 Controller对象 中。)
 *
 * 增强类 HandlerMethod
 * 增强方法：Object getBean()
 * </pre>
 */
public class GetBeanInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
    }

    /**
     * @param objInst 被增强的 HandlerMethod 类的实例
     * @param method getBean() 方法（QFTODO：HandlerMethod.getBean ？为什么要增强这个，该方法的返回值什么时候被增强为 EnhancedInstance 了的？）
     * @param ret 该方法的原始返回值。如果该方法触发异常，则可能为 null。
     */
    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        // 如果该方法（getBean）的返回值是被增强过的动态字段 QFTODO: ret 在哪里被增强的？
        if (ret instanceof EnhancedInstance) {
            ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (requestAttributes != null) {
                // 将 请求 加入到 当前Trace上下文 中
                ContextManager.getRuntimeContext()
                              .put(
                                  REQUEST_KEY_IN_RUNTIME_CONTEXT,
                                  requestAttributes.getRequest()
                              );
                // 将 应答 加入到 当前Trace上下文 中
                ContextManager.getRuntimeContext()
                              .put(
                                  RESPONSE_KEY_IN_RUNTIME_CONTEXT,
                                  requestAttributes.getResponse()
                              );
            }
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {

    }
}

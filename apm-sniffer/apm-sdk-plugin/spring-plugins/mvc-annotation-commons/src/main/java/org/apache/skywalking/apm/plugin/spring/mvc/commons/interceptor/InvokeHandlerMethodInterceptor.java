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

package org.apache.skywalking.apm.plugin.spring.mvc.commons.interceptor;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import java.lang.reflect.Method;

import static org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants.REQUEST_KEY_IN_RUNTIME_CONTEXT;
import static org.apache.skywalking.apm.plugin.spring.mvc.commons.Constants.RESPONSE_KEY_IN_RUNTIME_CONTEXT;

/**
 * <pre>
 * 增强类 AnnotationMethodHandlerAdapter（后续版本不在使用了）
 * 增强方法：ModelAndView invokeHandlerMethod(HttpServletRequest request, HttpServletResponse response, Object handler)
 * </pre>
 */
public class InvokeHandlerMethodInterceptor implements InstanceMethodsAroundInterceptor {
    /**
     * @param objInst 被增强了的 AnnotationMethodHandlerAdapter
     * @param method 被增强了的 AnnotationMethodHandlerAdapter 的 invokeHandlerMethod((HttpServletRequest, HttpServletResponse, Object handler)) 方法
     * @param allArguments
     * @param argumentsTypes
     * @param result change this result, if you want to truncate the method.
     * @throws Throwable
     */
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        // 如果第三个参数 handler 的类型是 被修改过的，且是 EnhancedInstance 类型
        if (allArguments[2] instanceof EnhancedInstance) {
            // 将 方法的 HttpServletRequest 和  HttpServletResponse 参数 加入到 RuntimeContext
            ContextManager.getRuntimeContext().put(RESPONSE_KEY_IN_RUNTIME_CONTEXT, allArguments[1]);
            ContextManager.getRuntimeContext().put(REQUEST_KEY_IN_RUNTIME_CONTEXT, allArguments[0]);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
    }
}

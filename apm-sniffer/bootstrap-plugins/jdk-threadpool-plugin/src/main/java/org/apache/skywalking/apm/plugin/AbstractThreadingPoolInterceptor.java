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

package org.apache.skywalking.apm.plugin;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import java.lang.reflect.Method;

public abstract class AbstractThreadingPoolInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {

        // 不需求增强时，直接返回
        if (notToEnhance(allArguments)) {
            return;
        }

        // 包装 Callable 或 Runnable 对象
        Object wrappedObject = wrap(allArguments[0]);
        // 替换 var1 为 wrappedObject
        if (wrappedObject != null) {
            allArguments[0] = wrappedObject;
        }
    }

    /**
     * wrap the Callable or Runnable object if needed
     * <pre>
     * (如果需要，包装 Callable 或 Runnable 对象)
     * </pre>
     *
     * @param param Callable 或 Runnable 对象
     * @return Wrapped object 或 null（如果不需要换行）
     */
    public abstract Object wrap(Object param);

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Object ret) throws Throwable {
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {
        // 不需求增强时，直接返回
        if (notToEnhance(allArguments)) {
            return;
        }

        // 设置 active span 的 log 为 t
        ContextManager.activeSpan().log(t);
    }

    /**
     * 当前tracingContext无active的span、方法参数为空、或者方法无参数、或者 var1 已被增强过，则不增强。
     * @param allArguments
     * @return true：不增强
     */
    private boolean notToEnhance(Object[] allArguments) {
        if (!ContextManager.isActive()) {
            return true;
        }

        if (allArguments == null || allArguments.length < 1) {
            return true;
        }

        // var1：Callable、Runnable
        Object argument = allArguments[0];

        // Avoid duplicate enhancement, such as the case where it has already been enhanced by RunnableWrapper or CallableWrapper with toolkit.
        // （避免重复增强，例如已经通过 RunnableWrapper 或 CallableWrapper 和 toolkit（jdk-threading-plugin） 对其进行增强的情况。）
        return argument instanceof EnhancedInstance && ((EnhancedInstance) argument).getSkyWalkingDynamicField() instanceof ContextSnapshot;
    }
}

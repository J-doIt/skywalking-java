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

package org.apache.skywalking.apm.plugin.jdk.threading;

import java.lang.reflect.Method;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * <pre>
 * 增强类：
 *      java.util.concurrent.Callable 以及子类，且配置在了 ‘plugin.jdkthreading.threading_class_prefixes’ 中的类
 *      or
 *      java.lang.Runnable 以及子类，且配置在了 ‘plugin.jdkthreading.threading_class_prefixes’ 中的类
 * 增强方法：
 *          V call()
 *          void run()
 * </pre>
 */
public class ThreadingMethodInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
        final Class<?>[] argumentsTypes, final MethodInterceptResult result) {

        // 创建 local span
        AbstractSpan span = ContextManager.createLocalSpan(generateOperationName(objInst, method));
        span.setComponent(ComponentsDefine.JDK_THREADING);

        // 从 objInst（Callable、Runnable）获取增强域的值（tracingContext快照）
        final Object storedField = objInst.getSkyWalkingDynamicField();
        if (storedField != null) {
            final ContextSnapshot contextSnapshot = (ContextSnapshot) storedField;
            // 从上游（上个线程） 获取 链路信息 后 ref 到当前 链路
            ContextManager.continued(contextSnapshot);
        }

    }

    @Override
    public Object afterMethod(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
        final Class<?>[] argumentsTypes, final Object ret) {
        // 结束 active span
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(final EnhancedInstance objInst, final Method method, final Object[] allArguments,
        final Class<?>[] argumentsTypes, final Throwable t) {

        if (ContextManager.isActive()) {
            // 设置 active span 的 log 为 t
            ContextManager.activeSpan().log(t);
        }
    }

    private String generateOperationName(final EnhancedInstance objInst, final Method method) {
        return "Threading/" + objInst.getClass().getName() + "/" + method.getName();
    }

}

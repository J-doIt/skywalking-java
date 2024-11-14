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

package org.apache.skywalking.apm.plugin.jdk.forkjoinpool;

import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.ContextSnapshot;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2.InstanceMethodsAroundInterceptorV2;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2.MethodInvocationContext;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * <pre>
 * 增强类：java.util.concurrent.ForkJoinPool$WorkQueue
 * 增强方法：
 *          runTask() // QFTODO：没找到
 *          void topLevelExec(ForkJoinTask<?> t, WorkQueue q, int n) // 运行给定的任务
 * </pre>
 */
public class ForkJoinWorkerQueueMethodInterceptor implements InstanceMethodsAroundInterceptorV2 {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            MethodInvocationContext context) throws Throwable {
        // 创建 local span
        AbstractSpan span = ContextManager.createLocalSpan(generateOperationName(objInst, method));
        span.setComponent(ComponentsDefine.JDK_THREADING);
        // 将 调用过程中的附加信息（local span）放到 方法调用上下文
        context.setContext(span);
        // var1：ForkJoinTask
        EnhancedInstance forkJoinTask = (EnhancedInstance) allArguments[0];
        if (forkJoinTask != null) {
            // 从 var1（ForkJoinTask） 的增强域 取值（上下文的快照）
            final Object storedField = forkJoinTask.getSkyWalkingDynamicField();
            if (storedField != null) {
                final ContextSnapshot contextSnapshot = (ContextSnapshot) storedField;
                // 将 上游的 上下文的快照 ref 到当前tracingContext 并继续
                ContextManager.continued(contextSnapshot);
            }
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            Object ret, MethodInvocationContext context) throws Throwable {
        // QFTODO：为什么要从 MethodInvocationContext 获取 span，而不是直接用 ContextManager.activeSpan() 获取 span？
        // 从 方法调用上下文 获取 调用过程中的附加信息（local span）
        AbstractSpan span = (AbstractSpan) context.getContext();
        if (span != null) {
            // 结束 active span
            ContextManager.stopSpan(span);
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
            Class<?>[] argumentsTypes, Throwable t, MethodInvocationContext context) {
        // 从 方法调用上下文 获取 调用过程中的附加信息（local span）
        AbstractSpan span = (AbstractSpan) context.getContext();
        if (span != null) {
            // 设置 span 的 log 为 t
            span.log(t);
        }
    }

    private String generateOperationName(final EnhancedInstance objInst, final Method method) {
        return "ForkJoinPool/" + objInst.getClass().getName() + "/" + method.getName();
    }
}

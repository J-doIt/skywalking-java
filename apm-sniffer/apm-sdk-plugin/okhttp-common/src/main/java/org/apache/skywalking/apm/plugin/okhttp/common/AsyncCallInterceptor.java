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

import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

/**
 * {@link AsyncCallInterceptor} get the `EnhanceRequiredInfo` instance from `SkyWalkingDynamicField` and then put it
 * into `AsyncCall` instance when the `AsyncCall` constructor called.
 * <p>
 * {@link AsyncCallInterceptor} also create an exit span by using the `EnhanceRequiredInfo` when the `execute` method
 * called.
 *
 * <pre>
 * （AsyncCallInterceptor 从‘ SkyWalkingDynamicField ’获取‘ EnhanceRequiredInfo ’实例，然后在‘ AsyncCall ’构造函数调用时将其放入‘ AsyncCall ’实例中。
 * AsyncCallInterceptor 还通过使用‘ EnhanceRequiredInfo ’在‘ execute ’方法调用时创建一个退出跨度。）
 *
 * 增强类 RealCall$AsyncCall
 * 增强构造函数：AsyncCall( responseCallback: Callback ).
 *      拦截器：AsyncCallInterceptor
 * 增强方法：fun run()，
 *      拦截器：AsyncCallInterceptor
 * </pre>
 */
public class AsyncCallInterceptor implements InstanceConstructorInterceptor, InstanceMethodsAroundInterceptor {

    /**
     * @param objInst RealCall$AsyncCall
     * @param allArguments Callback
     */
    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        /**
         * The first argument of constructor is not the `real` parameter when the enhance class is an inner class. This
         * is the JDK compiler mechanism.
         * (当 enhance 类是内部类时，constructor 的第一个参数不是 'real' 参数，而是 外部类实例？。这就是 JDK 编译器机制。)
         */
        EnhancedInstance realCallInstance = (EnhancedInstance) allArguments[1];
        // Callback 的动态字段 为 EnhanceRequiredInfo 对象
        Object enhanceRequireInfo = realCallInstance.getSkyWalkingDynamicField();

        // 将 EnhanceRequiredInfo 对象 加入到 RealCall$AsyncCall 的动态字段
        objInst.setSkyWalkingDynamicField(enhanceRequireInfo);
    }

    /**
     * @param objInst RealCall$AsyncCall
     * @param method fun run()
     * @param allArguments 空参
     * @param argumentsTypes 空参
     * @param result change this result, if you want to truncate the method.
     * @throws Throwable
     */
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
            MethodInterceptResult result) throws Throwable {
        // onConstruct() 中将 Callback 的动态字段 EnhanceRequiredInfo 对象 加入到 RealCall$AsyncCall 的动态字段
        EnhanceRequiredInfo enhanceRequiredInfo = (EnhanceRequiredInfo) objInst.getSkyWalkingDynamicField();
        // 创建 操作名为 Async/execute 的 local span
        ContextManager.createLocalSpan("Async/execute");
        // 从 enhanceRequiredInfo 获取快照 并执行 continued 继续
        ContextManager.continued(enhanceRequiredInfo.getContextSnapshot());

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

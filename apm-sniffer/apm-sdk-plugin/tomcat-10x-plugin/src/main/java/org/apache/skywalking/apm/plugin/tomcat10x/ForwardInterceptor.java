/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

package org.apache.skywalking.apm.plugin.tomcat10x;

import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * 拦截 ApplicationDispatcher 的 ≤cinit≥ 构造方法完成后的点，和 forward() 的执行前后的点
 */
public class ForwardInterceptor implements InstanceMethodsAroundInterceptor, InstanceConstructorInterceptor {

    /**
     * @param objInst 被增强了的 ApplicationDispatcher 类
     * @param method ApplicationDispatcher 的 forward(ServletRequest, ServletResponse) 方法
     * @param allArguments
     * @param argumentsTypes
     * @param result change this result, if you want to truncate the method.
     * @throws Throwable
     */
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        // 检查当前是否有活跃的 Span
        if (ContextManager.isActive()) {
            // 获取当前活跃的 Span
            AbstractSpan abstractTracingSpan = ContextManager.activeSpan();
            // 创建一个事件映射，用于存储 转发URL（forward-url：requestURI的值）
            Map<String, String> eventMap = new HashMap<String, String>();
            eventMap.put("forward-url", objInst.getSkyWalkingDynamicField() == null ? "" : String.valueOf(objInst.getSkyWalkingDynamicField()));
            // 记录当前时间戳和事件映射 到 Span 中
            abstractTracingSpan.log(System.currentTimeMillis(), eventMap);
            // 将转发请求标志设置为 true，表示正在进行转发操作
            ContextManager.getRuntimeContext().put(Constants.FORWARD_REQUEST_FLAG, true);
        }
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        // 从运行时上下文中移除转发请求标志
        ContextManager.getRuntimeContext().remove(Constants.FORWARD_REQUEST_FLAG);
        // 返回方法的执行结果
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {

    }

    /**
     * @param objInst {@link org.apache.skywalking.apm.plugin.tomcat10x.define.ApplicationDispatcherInstrumentation#ENHANCE_CLASS}：被增强了的 ApplicationDispatcher 类，该类是 EnhancedInstance 的实现类
     * @param allArguments ApplicationDispatcher 构造方法的参数实例数组
     */
    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) {
        /** {@link org.apache.skywalking.apm.agent.core.plugin.AbstractClassEnhancePluginDefine#CONTEXT_ATTR_NAME} */
        // 为 ApplicationDispatcher 的增强实例 的 动态字段 设置 值为 ApplicationDispatcher 构造方法 的第二个参数值（String requestURI）
        objInst.setSkyWalkingDynamicField(allArguments[1]);
    }
}

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

package org.apache.skywalking.apm.plugin.mybatis;

import java.lang.reflect.Method;
import java.util.Objects;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2.InstanceMethodsAroundInterceptorV2;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.v2.MethodInvocationContext;
import org.apache.skywalking.apm.agent.core.util.MethodUtil;

/**
 * <pre>
 * 增强类：org.apache.ibatis.session.defaults.DefaultSqlSession
 * 增强方法：
 *          ≤T> T selectOne(String statement, ...)
 *          ≤K, V> Map≤K, V> selectMap(String statement, ...)
 *          int insert(String statement, ...)
 *          int delete(String statement, ...)
 *          void select(String statement, ResultHandler handler)
 *          void select(String statement, Object parameter, ResultHandler handler)
 *          ≤E> List≤E> selectList(String statement)
 *          ≤E> List≤E> selectList(String statement, Object parameter)
 *          int update(String statement)
 *
 *
 * 上面这些方法，并非直接调用的 DefaultSqlSession.executor.query()、update()）
 * </pre>
 */
public class MyBatisShellMethodInterceptor implements InstanceMethodsAroundInterceptorV2 {

    /**
     * 将 本次调用 的 方法签名 放入 当前tracingContext.RuntimeContext 中，作为 下层调用 span 的 operationName。
     */
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInvocationContext context) throws Throwable {
        // 如果 当前tracingContext 的 RuntimeContext 中的 mybatis_shell_method_name 值 为空
        if (ContextManager.getRuntimeContext().get(Constants.MYBATIS_SHELL_METHOD_NAME) == null) {
            // 通过 方法调用上下文 存储 调用过程 中的 附加信息（标志-已收集）
            context.setContext(Constants.COLLECTED_FLAG);
            // 生成方法签名
            String operationName = MethodUtil.generateOperationName(method);
            // 设置 当前tracingContext 的 RuntimeContext 中的 mybatis_shell_method_name 值
            ContextManager.getRuntimeContext().put(Constants.MYBATIS_SHELL_METHOD_NAME, operationName);
        }
    }

    /**
     * 本次调用 结束，从 当前tracingContext.RuntimeContext 中 remove 本次调用 的 方法签名。
     */
    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret, MethodInvocationContext context) throws Throwable {
        // 如果 方法调用上下文 的 附加信息（标志-已收集）不为空
        if (Objects.nonNull(context.getContext())) {
            // 移除 当前tracingContext 的 RuntimeContext 中的 mybatis_shell_method_name 值
            ContextManager.getRuntimeContext().remove(Constants.MYBATIS_SHELL_METHOD_NAME);
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t, MethodInvocationContext context) {
        // 如果 方法调用上下文 的 附加信息（标志-已收集）不为空
        if (Objects.nonNull(context.getContext())) {
            // 移除 当前tracingContext 的 RuntimeContext 中的 mybatis_shell_method_name 值
            // 如果 不及时 remove，被调用的下层方法 被其他 上层方法调用，span 的 operationName 将是错误的。
            ContextManager.getRuntimeContext().remove(Constants.MYBATIS_SHELL_METHOD_NAME);
        }
    }
}

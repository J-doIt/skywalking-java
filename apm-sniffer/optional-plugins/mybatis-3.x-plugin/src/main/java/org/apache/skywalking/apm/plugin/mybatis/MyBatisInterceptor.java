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
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.util.MethodUtil;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

/**
 * <pre>
 * 增强类：org.apache.ibatis.session.defaults.DefaultSqlSession
 * 增强方法：
 *          void select(String statement, Object parameter, RowBounds rowBounds, ResultHandler handler)
 *          ≤E> List≤E> selectList(String statement, Object parameter, RowBounds rowBounds)
 *          int update(String statement, Object parameter)
 *
 *
 * 上面这些方法，直接调用的 DefaultSqlSession.executor.query()、update()）
 * </pre>
 *
 */
public class MyBatisInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
        String operationName;
        // 如果 当前tracingContext 的 RuntimeContext 中的 mybatis_shell_method_name 值 不为空
        if (ContextManager.getRuntimeContext().get(Constants.MYBATIS_SHELL_METHOD_NAME) != null) {
            // 使用 上层调用 生成方法签名
            operationName = String.valueOf(ContextManager.getRuntimeContext().get(Constants.MYBATIS_SHELL_METHOD_NAME));
        } else {
            // 生成方法签名
            operationName = MethodUtil.generateOperationName(method);
        }
        // 创建 local span
        AbstractSpan span = ContextManager.createLocalSpan(operationName);
        span.setComponent(ComponentsDefine.MYBATIS);
        // 设置 local span 的 mybatis.mapper 标签 为 var1（statement）
        Tags.MYBATIS_MAPPER.set(span, (String) allArguments[0]);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        // 结束 active span
        ContextManager.stopSpan();
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
        // 设置 active span 的 log 为 t
        ContextManager.activeSpan().log(t);
    }
}

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

package org.apache.skywalking.apm.plugin.jdbc;

import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.plugin.jdbc.define.Constants;
import org.apache.skywalking.apm.plugin.jdbc.define.StatementEnhanceInfos;

import java.lang.reflect.Method;

/**
 * <pre>
 * 增强类：java.sql.PreparedStatement、其实现类
 * 增强方法：
 *          {@link org.apache.skywalking.apm.plugin.jdbc.define.Constants#PS_IGNORABLE_SETTERS}
 * </pre>
 */
public class JDBCPreparedStatementIgnorableSetterInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public final void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        final StatementEnhanceInfos statementEnhanceInfos = (StatementEnhanceInfos) objInst.getSkyWalkingDynamicField();
        // 如果 objInst 的 增强域 不为空
        if (statementEnhanceInfos != null) {
          final int index = (Integer) allArguments[0];
          // 将 占位符 放入到 statementEnhanceInfos.parameters[index-1]
          statementEnhanceInfos.setParameter(index, Constants.SQL_PARAMETER_PLACEHOLDER);
        }
    }

    @Override
    public final Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) throws Throwable {
        return ret;
    }

    @Override
    public final void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
    }
}

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

import java.lang.reflect.Method;
import java.sql.CallableStatement;
import java.sql.Connection;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.plugin.jdbc.trace.SWCallableStatement;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.plugin.jdbc.trace.ConnectionInfo;

/**
 * {@link JDBCPrepareCallInterceptor} return {@link SWCallableStatement} instance that wrapper the real CallStatement
 * instance when the client call <code>prepareCall</code> method.
 *
 * <pre>
 * (JDBCPrepareCallInterceptor 返回 SWCallableStatement 实例，
 * 该SWCallableStatement实例 在 客户端 调用 prepareCall() 方法时 包装 真实的 CallStatement 实例。)
 *
 * 增强类：java.sql.Connection 的实现类
 * 增强方法：
 *          CallableStatement prepareCall(String sql)
 *          CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency)
 *          CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
 * </pre>
 */
public class JDBCPrepareCallInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {

    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        // 如果 java.sql.Connection 的实现类 的 增强域（ConnectionInfo） 为空，则不扩展 ret（CallableStatement）
        if (objInst.getSkyWalkingDynamicField() == null) {
            return ret;
        }
        // 将 ret（CallableStatement）替换为 SWCallableStatement 对象
        return new SWCallableStatement((Connection) objInst, (CallableStatement) ret, (ConnectionInfo) objInst.getSkyWalkingDynamicField(), (String) allArguments[0]);
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {

    }
}

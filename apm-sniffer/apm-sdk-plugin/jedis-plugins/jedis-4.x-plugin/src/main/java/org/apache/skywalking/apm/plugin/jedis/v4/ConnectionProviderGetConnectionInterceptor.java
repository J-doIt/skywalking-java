/*
 *   Licensed to the Apache Software Foundation (ASF) under one or more
 *   contributor license agreements.  See the NOTICE file distributed with
 *   this work for additional information regarding copyright ownership.
 *   The ASF licenses this file to You under the Apache License, Version 2.0
 *   (the "License"); you may not use this file except in compliance with
 *   the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.apache.skywalking.apm.plugin.jedis.v4;

import java.lang.reflect.Method;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

/**
 * <pre>
 * 增强类：redis.clients.jedis.providers.ConnectionProvider 及其子类
 * 增强方法：
 *          Connection getConnection(...)
 * </pre>
 */
public class ConnectionProviderGetConnectionInterceptor implements InstanceMethodsAroundInterceptor {
    @Override
    public void beforeMethod(final EnhancedInstance objInst,
                             final Method method,
                             final Object[] allArguments,
                             final Class<?>[] argumentsTypes,
                             final MethodInterceptResult result) throws Throwable {

    }

    @Override
    public Object afterMethod(final EnhancedInstance objInst,
                              final Method method,
                              final Object[] allArguments,
                              final Class<?>[] argumentsTypes,
                              final Object ret) throws Throwable {
        // 如果 Connection 是被增强了的
        if (ret instanceof EnhancedInstance) {
            EnhancedInstance connection = (EnhancedInstance) ret;
            // 从 Connection 增强对象 的 动态增强域 取值（ConnectionInformation）
            if (connection.getSkyWalkingDynamicField() != null
                && connection.getSkyWalkingDynamicField() instanceof ConnectionInformation) {
                // 将 Connection 增强对象 的 增强域 的 ConnectionInformation.clusterNodes 设置为 objInst（ConnectionProvider）的 增强域的值
                ((ConnectionInformation) connection.getSkyWalkingDynamicField()).setClusterNodes(
                    (String) objInst.getSkyWalkingDynamicField());
            }
        }
        return ret;
    }

    @Override
    public void handleMethodException(final EnhancedInstance objInst,
                                      final Method method,
                                      final Object[] allArguments,
                                      final Class<?>[] argumentsTypes,
                                      final Throwable t) {

    }
}

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

package org.apache.skywalking.apm.plugin.jdbc.mysql.v8;

import com.mysql.cj.conf.HostInfo;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.StaticMethodsAroundInterceptor;
import org.apache.skywalking.apm.plugin.jdbc.mysql.ConnectionCache;
import org.apache.skywalking.apm.plugin.jdbc.trace.ConnectionInfo;

import java.lang.reflect.Method;

/**
 * <pre>
 * 增强类：com.mysql.cj.jdbc.ConnectionImpl
 * 增强方法：static com.mysql.cj.jdbc.JdbcConnection getInstance(HostInfo hostInfo)
 *
 * com.mysql.cj.jdbc.JdbcConnection 继承了 java.sql.Connection
 * </pre>
 */
public class ConnectionCreateInterceptor implements StaticMethodsAroundInterceptor {

    @Override
    public void beforeMethod(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes,
        MethodInterceptResult result) {

    }

    /**
     * @param clazz com.mysql.cj.jdbc.ConnectionImpl 的 增强类
     * @param method getInstance
     * @param allArguments [hostInfo]
     * @param parameterTypes [HostInfo]
     * @param ret JdbcConnection 实例（可能是被增强过的）
     */
    @Override
    public Object afterMethod(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes,
        Object ret) {
        // 如果 ret-JdbcConnection 是被增强过的
        if (ret instanceof EnhancedInstance) {
            final HostInfo hostInfo = (HostInfo) allArguments[0];
            // 从 ConnectionCache 中拿到 该连接的 ConnectionInfo
            ConnectionInfo connectionInfo = ConnectionCache.get(hostInfo.getHostPortPair(), hostInfo.getDatabase());
            // 设置 ret 的 动态增强域 的值为 connectionInfo
            ((EnhancedInstance) ret).setSkyWalkingDynamicField(connectionInfo);
        }
        return ret;
    }

    @Override
    public void handleMethodException(Class clazz, Method method, Object[] allArguments, Class<?>[] parameterTypes,
        Throwable t) {

    }
}

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
import java.sql.Connection;

import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.dynamic.ConfigurationDiscoveryService;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.plugin.jdbc.connectionurl.parser.URLParser;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

/**
 * {@link JDBCDriverInterceptor} set <code>ConnectionInfo</code> to {@link Connection} object when {@link
 * java.sql.Driver} to create connection, instead of the  {@link Connection} instance.
 *
 * <pre>
 * (JDBCDriverInterceptor 在 java.sql.Driver 创建连接时，将 ConnectionInfo 设置为 "Connection对象" ，而不是 "Connection实例" 。)
 *
 * QFTODO：Connection对象？Connection实例？
 *
 * 增强类：java.sql.Driver 的实现类（具体的实现类见各数据库插件）
 * 增强方法：Connection connect(String url, java.util.Properties info)
 * </pre>
 */
public class JDBCDriverInterceptor implements InstanceMethodsAroundInterceptor {

    /**
     * @param objInst java.sql.Driver 的实现类 的增强类实例
     * @param method connect()
     * @param allArguments [url, info]
     * @param argumentsTypes [String, Properties]
     */
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {
        // 动态配置观察器（关注 plugin.jdbc.trace_sql_parameters 配置）
        TraceSqlParametersWatcher traceSqlParametersWatcher = new TraceSqlParametersWatcher("plugin.jdbc.trace_sql_parameters");
        ConfigurationDiscoveryService configurationDiscoveryService = ServiceManager.INSTANCE.findService(
                ConfigurationDiscoveryService.class);
        // 注册动态配置观察器
        configurationDiscoveryService.registerAgentConfigChangeWatcher(traceSqlParametersWatcher);
    }

    /**
     * @param objInst java.sql.Driver 的实现类 的增强类实例
     * @param method connect()
     * @param allArguments [url, info]
     * @param argumentsTypes [String, Properties]
     * @param ret Connection 对象
     */
    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        if (ret != null && ret instanceof EnhancedInstance) {
            // 将 解析后的参数url（ConnectionInfo类型） 设置为 Connection 的 增强域
            ((EnhancedInstance) ret).setSkyWalkingDynamicField(URLParser.parser((String) allArguments[0]));
        }

        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {

    }
}

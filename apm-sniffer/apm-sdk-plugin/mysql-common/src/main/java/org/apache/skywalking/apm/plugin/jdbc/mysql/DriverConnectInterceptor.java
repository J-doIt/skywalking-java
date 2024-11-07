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

package org.apache.skywalking.apm.plugin.jdbc.mysql;

import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.dynamic.ConfigurationDiscoveryService;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.plugin.jdbc.TraceSqlParametersWatcher;
import org.apache.skywalking.apm.plugin.jdbc.connectionurl.parser.URLParser;

import java.lang.reflect.Method;

/**
 * <pre>
 * 驱动连接拦截器
 * 增强类：com.mysql.cj.jdbc.NonRegisteringDriver
 * 增强方法：public java.sql.Connection connect(String url, Properties info)
 * </pre>
 */
public class DriverConnectInterceptor implements InstanceMethodsAroundInterceptor {

    /**
     * @param objInst NonRegisteringDriver 增强实例
     * @param method connect
     * @param allArguments [url, info]
     * @param argumentsTypes [String, Properties]
     * @param result java.sql.Connection
     */
    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        MethodInterceptResult result) throws Throwable {

        // 解析 url 为 ConnectionInfo 对象，并保存到 ConnectionCache.CONNECTIONS_MAP 中
        ConnectionCache.save(URLParser.parser(allArguments[0].toString()));

        // 动态配置观察器（关注 plugin.jdbc.trace_sql_parameters 配置）
        TraceSqlParametersWatcher traceSqlParametersWatcher = new TraceSqlParametersWatcher("plugin.jdbc.trace_sql_parameters");
        ConfigurationDiscoveryService configurationDiscoveryService = ServiceManager.INSTANCE.findService(
                ConfigurationDiscoveryService.class);
        // 注册动态配置观察器
        configurationDiscoveryService.registerAgentConfigChangeWatcher(traceSqlParametersWatcher);
    }

    /**
     * @param ret java.sql.Connection
     */
    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
        Object ret) throws Throwable {
        // 如果 ret-Connection 是被增强了的
        if (ret != null && ret instanceof EnhancedInstance) {
            // 将 包含解析后的url的 ConnectionInfo对象 加入到 objInst（NonRegisteringDriver）的 增强域
            ((EnhancedInstance) ret).setSkyWalkingDynamicField(URLParser.parser((String) allArguments[0]));
        }
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {

    }
}

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
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.plugin.jdbc.trace.ConnectionInfo;

/**
 * {@link ConnectionServiceMethodInterceptor} create an exit span when the following methods execute: 1. close 2.
 * rollback 3. releaseSavepoint 4. commit
 *
 * <pre>
 * (ConnectionServiceMethodInterceptor 在执行以下方法时创建一个 exit span：
 *      1. close
 *      2. rollback
 *      3. releaseSavepoint
 *      4. commit
 * )
 * 增强类：java.sql.Connection 的实现类
 * 增强方法：
 *      void close()
 *      void rollback()、void rollback(Savepoint savepoint)
 *      void releaseSavepoint(Savepoint savepoint)
 *      void commit()
 * </pre>
 */
public class ConnectionServiceMethodInterceptor implements InstanceMethodsAroundInterceptor {

    @Override
    public final void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        ConnectionInfo connectInfo = (ConnectionInfo) objInst.getSkyWalkingDynamicField();
        // 如果 java.sql.Connection 的实现类 的 增强域（ConnectionInfo） 不为空
        if (connectInfo != null) {
            // 创建 exit span
            AbstractSpan span = ContextManager.createExitSpan(connectInfo.getDBType() + "/JDBC/Connection/" + method.getName(), connectInfo
                .getDatabasePeer());
            // 设置 exit span 的 db.type 标签
            Tags.DB_TYPE.set(span, connectInfo.getDBType());
            // 设置 exit span 的 db.instance 标签
            Tags.DB_INSTANCE.set(span, connectInfo.getDatabaseName());
            // 设置 exit span 的 db.statement 标签
            Tags.DB_STATEMENT.set(span, "");
            span.setComponent(connectInfo.getComponent());
            SpanLayer.asDB(span);
        }
    }

    @Override
    public final Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Object ret) throws Throwable {
        ConnectionInfo connectInfo = (ConnectionInfo) objInst.getSkyWalkingDynamicField();
        // 如果 java.sql.Connection 的实现类 的 增强域（ConnectionInfo） 不为空
        if (connectInfo != null) {
            // 结束 active span
            ContextManager.stopSpan();
        }
        return ret;
    }

    @Override
    public final void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
        Class<?>[] argumentsTypes, Throwable t) {
        // 设置 active span 的 log 为 t
        ContextManager.activeSpan().log(t);
    }

}

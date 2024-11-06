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

package org.apache.skywalking.apm.plugin.jdbc.trace;

import java.sql.SQLException;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.ContextManager;

/**
 * {@link CallableStatementTracing} create an exit span when the client call the method in the class that extend {@link
 * java.sql.CallableStatement}.
 *
 * <pre>
 * (CallableStatementTracing 在 客户端 调用 SWCallableStatement扩展的CallableStatement方法时 创建一个 exit span。)
 * </pre>
 */
public class CallableStatementTracing {

    /**
     * <pre>
     * 扩展原始方法
     *
     * 扩展方法的执行步骤：
     *   1. 创建 exit span
     *   2. 设置 span 的各种属性
     *   3. 执行 CallableStatement 的原始方法
     * </pre>
     *
     * @param realStatement Connection.prepareCall() 的 原始返回对象
     * @param connectInfo java.sql.Connection实现类 的 增强对象 的增强域
     * @param method 被 SWCallableStatement 扩展的方法名
     * @param sql Connection.prepareCall() 的 var1
     * @param exec 原始CallableStatement执行原始方法的函数
     * @return 执行 exec 的返回值
     * @param <R> 被扩展的方法的返回值类型
     */
    public static <R> R execute(java.sql.CallableStatement realStatement, ConnectionInfo connectInfo, String method,
        String sql, Executable<R> exec) throws SQLException {
        // 创建 exit span
        AbstractSpan span = ContextManager.createExitSpan(connectInfo.getDBType() + "/JDBC/CallableStatement/" + method, connectInfo
            .getDatabasePeer());
        try {
            // 设置 exit span 的 db.type 标签
            Tags.DB_TYPE.set(span, connectInfo.getDBType());
            SpanLayer.asDB(span);
            // 设置 exit span 的 db.instance 标签
            Tags.DB_INSTANCE.set(span, connectInfo.getDatabaseName());
            // 设置 exit span 的 db.statement 标签
            Tags.DB_STATEMENT.set(span, sql);
            span.setComponent(connectInfo.getComponent());
            // 执行 原始CallableStatement 的 原始方法，并返回 原始的返回值对象
            return exec.exe(realStatement, sql);
        } catch (SQLException e) {
            // 设置 active span 的 log 为 e
            span.log(e);
            throw e;
        } finally {
            // 结束 active span
            ContextManager.stopSpan(span);
        }
    }

    /**
     * 原始CallableStatement执行原始方法的函数
     * @param <R> 被 SWCallableStatement 扩展的方法的返回值类型
     */
    public interface Executable<R> {
        /**
         * 执行 原始CallableStatement 的 原始方法，并返回 原始的返回值对象
         * @param realConnection Connection.prepareCall() 的 原始返回对象
         * @param sql Connection.prepareCall() 的 var1
         * @return 原始返回对象 执行 原方法 得到的返回值对象
         */
        R exe(java.sql.CallableStatement realConnection, String sql) throws SQLException;
    }
}

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
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;

/**
 * {@link PreparedStatementTracing} create an exit span when the client call the method in the class that extend {@link
 * java.sql.Statement}.
 *
 * <pre>
 * (PreparedStatementTracing 在客户端 调用 SWStatement扩展的 Statement方法时 创建一个 exit span。)
 * </pre>
 */
public class StatementTracing {
    /**
     * <pre>
     * 扩展原始方法
     *
     * 扩展方法的执行步骤：
     *   1. 创建 exit span
     *   2. 设置 span 的各种属性
     *   3. 执行 Statement 的原始方法
     * </pre>
     * @param realStatement
     * @param connectInfo
     * @param method
     * @param sql
     * @param exec
     * @return
     * @param <R>
     * @throws SQLException
     */
    public static <R> R execute(java.sql.Statement realStatement, ConnectionInfo connectInfo, String method, String sql,
        Executable<R> exec) throws SQLException {
        try {
            // 创建 exit span
            AbstractSpan span = ContextManager.createExitSpan(connectInfo.getDBType() + "/JDBC/Statement/" + method, connectInfo
                .getDatabasePeer());
            // 设置 exit span 的 db.type 标签
            Tags.DB_TYPE.set(span, connectInfo.getDBType());
            // 设置 exit span 的 db.instance 标签
            Tags.DB_INSTANCE.set(span, connectInfo.getDatabaseName());
            // 设置 exit span 的 db.statement 标签
            Tags.DB_STATEMENT.set(span, sql);
            span.setComponent(connectInfo.getComponent());
            SpanLayer.asDB(span);
            // 执行 原始Statement 的 原始方法，并返回 原始的返回值对象
            return exec.exe(realStatement, sql);
        } catch (SQLException e) {
            AbstractSpan span = ContextManager.activeSpan();
            // 设置 active span 的 log 为 e
            span.log(e);
            throw e;
        } finally {
            // 结束 active span
            ContextManager.stopSpan();
        }
    }

    /**
     * 原始Statement执行原始方法的函数
     * @param <R> 被 SWStatement 扩展的方法的返回值类型
     */
    public interface Executable<R> {
        R exe(java.sql.Statement realStatement, String sql) throws SQLException;
    }
}

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
import java.util.Objects;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.plugin.jdbc.JDBCPluginConfig;
import org.apache.skywalking.apm.plugin.jdbc.PreparedStatementParameterBuilder;
import org.apache.skywalking.apm.plugin.jdbc.define.StatementEnhanceInfos;

/**
 * {@link PreparedStatementTracing} create an exit span when the client call the method in the class that extend {@link
 * java.sql.PreparedStatement}.
 * <pre>
 * (PreparedStatementTracing 在 客户端 调用 SWPreparedStatement扩展的 PreparedStatement方法时 创建 exit span。)
 * </pre>
 */
public class PreparedStatementTracing {

    /**
     * <pre>
     * 扩展原始方法
     *
     * 扩展方法的执行步骤：
     *   1. 创建 exit span
     *   2. 设置 span 的各种属性
     *   3. 必要时，收集sql的参数到 span
     *   4. 执行 PreparedStatement 的原始方法
     * </pre>
     * @param realStatement
     * @param connectInfo
     * @param method
     * @param sql
     * @param exec
     * @param statementEnhanceInfos
     * @return
     * @param <R>
     * @throws SQLException
     */
    public static <R> R execute(java.sql.PreparedStatement realStatement, ConnectionInfo connectInfo, String method,
            String sql, Executable<R> exec, StatementEnhanceInfos statementEnhanceInfos) throws SQLException {
        // 创建 exit span
        final AbstractSpan span = ContextManager.createExitSpan(
                connectInfo.getDBType() + "/JDBC/PreparedStatement/" + method, connectInfo
                        .getDatabasePeer());
        try {
            // 设置 exit span 的 db.type 标签
            Tags.DB_TYPE.set(span, connectInfo.getDBType());
            // 设置 exit span 的 db.instance 标签
            Tags.DB_INSTANCE.set(span, connectInfo.getDatabaseName());
            // 设置 exit span 的 db.statement 标签
            Tags.DB_STATEMENT.set(span, sql);
            span.setComponent(connectInfo.getComponent());
            SpanLayer.asDB(span);
            // 判断 是否需要收集sql的参数
            if (JDBCPluginConfig.Plugin.JDBC.TRACE_SQL_PARAMETERS && Objects.nonNull(statementEnhanceInfos)) {
                final Object[] parameters = statementEnhanceInfos.getParameters();
                if (parameters != null && parameters.length > 0) {
                    int maxIndex = statementEnhanceInfos.getMaxIndex();
                    // 设置 exit span 的 db.sql.parameters 标签
                    Tags.SQL_PARAMETERS.set(span, getParameterString(parameters, maxIndex));
                }
            }
            // 执行 原始PreparedStatement 的 原始方法，并返回 原始的返回值对象
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
     * 将 参数 parameters[] 中的 参数用 "," 分隔，超过 maxIndex 时截取。
     * @param parameters
     * @param maxIndex
     * @return
     */
    private static String getParameterString(Object[] parameters, int maxIndex) {
        return new PreparedStatementParameterBuilder()
                .setParameters(parameters)
                .setMaxIndex(maxIndex)
                .build();
    }

    /**
     * 原始PreparedStatement执行原始方法的函数
     * @param <R> 被 SWPreparedStatement 扩展的方法的返回值类型
     */
    public interface Executable<R> {

        /**
         * 执行 原始PreparedStatement 的 原始方法，并返回 原始的返回值对象
         * @param realConnection Connection.prepareStatement() 的 原始返回对象
         * @param sql Connection.prepareStatement() 的 var1
         * @return 原始返回对象 执行 原方法 得到的返回值对象
         */
        R exe(java.sql.PreparedStatement realConnection, String sql) throws SQLException;
    }
}

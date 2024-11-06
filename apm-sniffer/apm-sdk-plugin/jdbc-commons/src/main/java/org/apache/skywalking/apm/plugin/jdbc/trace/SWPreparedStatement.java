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

import static org.apache.skywalking.apm.plugin.jdbc.define.Constants.SQL_PARAMETER_PLACEHOLDER;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.Calendar;
import org.apache.skywalking.apm.plugin.jdbc.define.StatementEnhanceInfos;

/**
 * {@link SWPreparedStatement} wrapper the {@link PreparedStatement} created by client. and it will interceptor the
 * following methods for trace. 1. {@link #execute()} 2. {@link #execute(String)} 3. {@link #execute(String, int[])} 4.
 * {@link #execute(String, String[])} 5. {@link #execute(String, int)} 6. {@link #executeQuery()} 7. {@link
 * #executeQuery(String)} 8. {@link #executeUpdate()} 9. {@link #executeUpdate(String)} 10. {@link
 * #executeUpdate(String, int[])} 11. {@link #executeUpdate(String, String[])} 12. {@link #executeUpdate(String, int)}
 * 13. {@link #addBatch()} 14. {@link #addBatch(String)} ()}
 *
 * <pre>
 * 增强 PreparedStatement。
 *
 * 扩展方法的执行步骤见{@link org.apache.skywalking.apm.plugin.jdbc.trace.PreparedStatementTracing#execute(PreparedStatement, ConnectionInfo, String, String, PreparedStatementTracing.Executable, StatementEnhanceInfos)}
 *
 * PreparedStatement 表示预编译 SQL 语句的对象。
 * </pre>
 */
public class SWPreparedStatement implements PreparedStatement {

    /** java.sql.Connection实现类 的 增强对象 */
    private Connection realConnection;
    /* Connection.prepareStatement() 返回的原始对象 */
    private PreparedStatement realStatement;
    /** java.sql.Connection实现类 的 增强对象 的增强域 */
    private ConnectionInfo connectInfo;
    /* Connection.prepareStatement() 的 var1 */
    private String sql;

    /** 增强域 实例 */
    private StatementEnhanceInfos statementEnhanceInfos;

    public SWPreparedStatement(Connection realConnection, PreparedStatement realStatement, ConnectionInfo connectInfo,
            String sql) {
        this.realConnection = realConnection;
        this.realStatement = realStatement;
        this.connectInfo = connectInfo;
        this.sql = sql;
        // 创建 增强域 实例
        this.statementEnhanceInfos = new StatementEnhanceInfos(connectInfo, sql, "PreparedStatement");
    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        // 扩展原始方法
        return PreparedStatementTracing.execute(realStatement, connectInfo, "executeQuery", sql,
                new PreparedStatementTracing.Executable<ResultSet>() {
                    @Override
                    public ResultSet exe(PreparedStatement realStatement, String sql) throws SQLException {
                        return realStatement.executeQuery(sql);
                    }
                }, statementEnhanceInfos);
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        // 扩展原始方法
        return PreparedStatementTracing.execute(realStatement, connectInfo, "executeUpdate", sql,
                new PreparedStatementTracing.Executable<Integer>() {
                    @Override
                    public Integer exe(PreparedStatement realStatement, String sql) throws SQLException {
                        return realStatement.executeUpdate(sql);
                    }
                }, statementEnhanceInfos);
    }

    @Override
    public void close() throws SQLException {
        realStatement.close();
    }

    @Override
    public int getMaxFieldSize() throws SQLException {
        return realStatement.getMaxFieldSize();
    }

    @Override
    public void setMaxFieldSize(int max) throws SQLException {
        realStatement.setMaxFieldSize(max);
    }

    @Override
    public int getMaxRows() throws SQLException {
        return realStatement.getMaxRows();
    }

    @Override
    public void setMaxRows(int max) throws SQLException {
        realStatement.setMaxRows(max);
    }

    @Override
    public void setEscapeProcessing(boolean enable) throws SQLException {
        realStatement.setEscapeProcessing(enable);
    }

    @Override
    public int getQueryTimeout() throws SQLException {
        return realStatement.getQueryTimeout();
    }

    @Override
    public void setQueryTimeout(int seconds) throws SQLException {
        realStatement.setQueryTimeout(seconds);
    }

    @Override
    public void cancel() throws SQLException {
        realStatement.cancel();
    }

    @Override
    public SQLWarning getWarnings() throws SQLException {
        return realStatement.getWarnings();
    }

    @Override
    public void clearWarnings() throws SQLException {
        realStatement.clearWarnings();
    }

    @Override
    public void setCursorName(String name) throws SQLException {
        realStatement.setCursorName(name);
    }

    @Override
    public boolean execute(String sql) throws SQLException {
        // 扩展原始方法
        return PreparedStatementTracing.execute(realStatement, connectInfo, "execute", sql,
                new PreparedStatementTracing.Executable<Boolean>() {
                    @Override
                    public Boolean exe(PreparedStatement realStatement, String sql) throws SQLException {
                        return realStatement.execute(sql);
                    }
                }, statementEnhanceInfos);
    }

    @Override
    public ResultSet getResultSet() throws SQLException {
        return realStatement.getResultSet();
    }

    @Override
    public int getUpdateCount() throws SQLException {
        return realStatement.getUpdateCount();
    }

    @Override
    public boolean getMoreResults() throws SQLException {
        return realStatement.getMoreResults();
    }

    @Override
    public void setFetchDirection(int direction) throws SQLException {
        realStatement.setFetchDirection(direction);
    }

    @Override
    public int getFetchDirection() throws SQLException {
        return realStatement.getFetchDirection();
    }

    @Override
    public void setFetchSize(int rows) throws SQLException {
        realStatement.setFetchSize(rows);
    }

    @Override
    public int getFetchSize() throws SQLException {
        return realStatement.getFetchSize();
    }

    @Override
    public int getResultSetConcurrency() throws SQLException {
        return realStatement.getResultSetConcurrency();
    }

    @Override
    public int getResultSetType() throws SQLException {
        return realStatement.getResultSetType();
    }

    @Override
    public void addBatch(String sql) throws SQLException {
        realStatement.addBatch(sql);
    }

    @Override
    public void clearBatch() throws SQLException {
        realStatement.clearBatch();
    }

    @Override
    public int[] executeBatch() throws SQLException {
        // 扩展原始方法
        return PreparedStatementTracing.execute(realStatement, connectInfo, "executeBatch", "",
                new PreparedStatementTracing.Executable<int[]>() {
                    @Override
                    public int[] exe(PreparedStatement realStatement, String sql) throws SQLException {
                        return realStatement.executeBatch();
                    }
                }, statementEnhanceInfos);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return realConnection;
    }

    @Override
    public boolean getMoreResults(int current) throws SQLException {
        return realStatement.getMoreResults(current);
    }

    @Override
    public ResultSet getGeneratedKeys() throws SQLException {
        return realStatement.getGeneratedKeys();
    }

    @Override
    public int executeUpdate(String sql, final int autoGeneratedKeys) throws SQLException {
        // 扩展原始方法
        return PreparedStatementTracing.execute(realStatement, connectInfo, "executeUpdate", sql,
                new PreparedStatementTracing.Executable<Integer>() {
                    @Override
                    public Integer exe(PreparedStatement realStatement, String sql) throws SQLException {
                        return realStatement.executeUpdate(sql, autoGeneratedKeys);
                    }
                }, statementEnhanceInfos);
    }

    @Override
    public int executeUpdate(String sql, final int[] columnIndexes) throws SQLException {
        // 扩展原始方法
        return PreparedStatementTracing.execute(realStatement, connectInfo, "executeUpdate", sql,
                new PreparedStatementTracing.Executable<Integer>() {
                    @Override
                    public Integer exe(PreparedStatement realStatement, String sql) throws SQLException {
                        return realStatement.executeUpdate(sql, columnIndexes);
                    }
                }, statementEnhanceInfos);
    }

    @Override
    public int executeUpdate(String sql, final String[] columnNames) throws SQLException {
        // 扩展原始方法
        return PreparedStatementTracing.execute(realStatement, connectInfo, "executeUpdate", sql,
                new PreparedStatementTracing.Executable<Integer>() {
                    @Override
                    public Integer exe(PreparedStatement realStatement, String sql) throws SQLException {
                        return realStatement.executeUpdate(sql, columnNames);
                    }
                }, statementEnhanceInfos);
    }

    @Override
    public boolean execute(String sql, final int autoGeneratedKeys) throws SQLException {
        // 扩展原始方法
        return PreparedStatementTracing.execute(realStatement, connectInfo, "execute", sql,
                new PreparedStatementTracing.Executable<Boolean>() {
                    @Override
                    public Boolean exe(PreparedStatement realStatement, String sql) throws SQLException {
                        return realStatement.execute(sql, autoGeneratedKeys);
                    }
                }, statementEnhanceInfos);
    }

    @Override
    public boolean execute(String sql, final int[] columnIndexes) throws SQLException {
        // 扩展原始方法
        return PreparedStatementTracing.execute(realStatement, connectInfo, "execute", sql,
                new PreparedStatementTracing.Executable<Boolean>() {
                    @Override
                    public Boolean exe(PreparedStatement realStatement, String sql) throws SQLException {
                        return realStatement.execute(sql, columnIndexes);
                    }
                }, statementEnhanceInfos);
    }

    @Override
    public boolean execute(String sql, final String[] columnNames) throws SQLException {
        // 扩展原始方法
        return PreparedStatementTracing.execute(realStatement, connectInfo, "execute", sql,
                new PreparedStatementTracing.Executable<Boolean>() {
                    @Override
                    public Boolean exe(PreparedStatement realStatement, String sql) throws SQLException {
                        return realStatement.execute(sql, columnNames);
                    }
                }, statementEnhanceInfos);
    }

    @Override
    public int getResultSetHoldability() throws SQLException {
        return realStatement.getResultSetHoldability();
    }

    @Override
    public boolean isClosed() throws SQLException {
        return realStatement.isClosed();
    }

    @Override
    public void setPoolable(boolean poolable) throws SQLException {
        realStatement.setPoolable(poolable);
    }

    @Override
    public boolean isPoolable() throws SQLException {
        return realStatement.isPoolable();
    }

    @Override
    public void closeOnCompletion() throws SQLException {
        realStatement.closeOnCompletion();
    }

    @Override
    public boolean isCloseOnCompletion() throws SQLException {
        return realStatement.isCloseOnCompletion();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return realStatement.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return realStatement.isWrapperFor(iface);
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        // 扩展原始方法
        return PreparedStatementTracing.execute(realStatement, connectInfo, "executeQuery", sql,
                new PreparedStatementTracing.Executable<ResultSet>() {
                    @Override
                    public ResultSet exe(PreparedStatement realStatement, String sql) throws SQLException {
                        return realStatement.executeQuery();
                    }
                }, statementEnhanceInfos);
    }

    @Override
    public int executeUpdate() throws SQLException {
        // 扩展原始方法
        return PreparedStatementTracing.execute(realStatement, connectInfo, "executeUpdate", sql,
                new PreparedStatementTracing.Executable<Integer>() {
                    @Override
                    public Integer exe(PreparedStatement realStatement, String sql) throws SQLException {
                        return realStatement.executeUpdate();
                    }
                }, statementEnhanceInfos);
    }

    @Override
    public void setNull(int parameterIndex, int sqlType) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, "NULL");
        realStatement.setNull(parameterIndex, sqlType);
    }

    @Override
    public void setBoolean(int parameterIndex, boolean x) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, x);
        realStatement.setBoolean(parameterIndex, x);
    }

    @Override
    public void setByte(int parameterIndex, byte x) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, SQL_PARAMETER_PLACEHOLDER);
        realStatement.setByte(parameterIndex, x);
    }

    @Override
    public void setShort(int parameterIndex, short x) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, x);
        realStatement.setShort(parameterIndex, x);
    }

    @Override
    public void setInt(int parameterIndex, int x) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, x);
        realStatement.setInt(parameterIndex, x);
    }

    @Override
    public void setLong(int parameterIndex, long x) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, x);
        realStatement.setLong(parameterIndex, x);
    }

    @Override
    public void setFloat(int parameterIndex, float x) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, x);
        realStatement.setFloat(parameterIndex, x);
    }

    @Override
    public void setDouble(int parameterIndex, double x) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, x);
        realStatement.setDouble(parameterIndex, x);
    }

    @Override
    public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, x);
        realStatement.setBigDecimal(parameterIndex, x);
    }

    @Override
    public void setString(int parameterIndex, String x) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, x);
        realStatement.setString(parameterIndex, x);
    }

    @Override
    public void setBytes(int parameterIndex, byte[] x) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, SQL_PARAMETER_PLACEHOLDER);
        realStatement.setBytes(parameterIndex, x);
    }

    @Override
    public void setDate(int parameterIndex, Date x) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, x);
        realStatement.setDate(parameterIndex, x);
    }

    @Override
    public void setTime(int parameterIndex, Time x) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, x);
        realStatement.setTime(parameterIndex, x);
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, x);
        realStatement.setTimestamp(parameterIndex, x);
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, SQL_PARAMETER_PLACEHOLDER);
        realStatement.setAsciiStream(parameterIndex, x, length);
    }

    @Override
    @Deprecated
    public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, SQL_PARAMETER_PLACEHOLDER);
        realStatement.setUnicodeStream(parameterIndex, x, length);
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, SQL_PARAMETER_PLACEHOLDER);
        realStatement.setBinaryStream(parameterIndex, x, length);
    }

    @Override
    public void clearParameters() throws SQLException {
        realStatement.clearParameters();
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, x);
        realStatement.setObject(parameterIndex, x, targetSqlType);
    }

    @Override
    public void setObject(int parameterIndex, Object x) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, x);
        realStatement.setObject(parameterIndex, x);
    }

    @Override
    public boolean execute() throws SQLException {
        // 扩展原始方法
        return PreparedStatementTracing.execute(realStatement, connectInfo, "execute", sql,
                new PreparedStatementTracing.Executable<Boolean>() {
                    @Override
                    public Boolean exe(PreparedStatement realStatement, String sql) throws SQLException {
                        return realStatement.execute();
                    }
                }, statementEnhanceInfos);
    }

    @Override
    public void addBatch() throws SQLException {
        realStatement.addBatch();
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, SQL_PARAMETER_PLACEHOLDER);
        realStatement.setCharacterStream(parameterIndex, reader, length);
    }

    @Override
    public void setRef(int parameterIndex, Ref x) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, SQL_PARAMETER_PLACEHOLDER);
        realStatement.setRef(parameterIndex, x);
    }

    @Override
    public void setBlob(int parameterIndex, Blob x) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, SQL_PARAMETER_PLACEHOLDER);
        realStatement.setBlob(parameterIndex, x);
    }

    @Override
    public void setClob(int parameterIndex, Clob x) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, SQL_PARAMETER_PLACEHOLDER);
        realStatement.setClob(parameterIndex, x);
    }

    @Override
    public void setArray(int parameterIndex, Array x) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, x);
        realStatement.setArray(parameterIndex, x);
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLException {
        return realStatement.getMetaData();
    }

    @Override
    public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, x);
        realStatement.setDate(parameterIndex, x, cal);
    }

    @Override
    public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, x);
        realStatement.setTime(parameterIndex, x, cal);
    }

    @Override
    public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, x);
        realStatement.setTimestamp(parameterIndex, x, cal);
    }

    @Override
    public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, "NULL");
        realStatement.setNull(parameterIndex, sqlType, typeName);
    }

    @Override
    public void setURL(int parameterIndex, URL x) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, x);
        realStatement.setURL(parameterIndex, x);
    }

    @Override
    public ParameterMetaData getParameterMetaData() throws SQLException {
        return realStatement.getParameterMetaData();
    }

    @Override
    public void setRowId(int parameterIndex, RowId x) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, x);
        realStatement.setRowId(parameterIndex, x);
    }

    @Override
    public void setNString(int parameterIndex, String value) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, value);
        realStatement.setNString(parameterIndex, value);
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, SQL_PARAMETER_PLACEHOLDER);
        realStatement.setNCharacterStream(parameterIndex, value, length);
    }

    @Override
    public void setNClob(int parameterIndex, NClob value) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, SQL_PARAMETER_PLACEHOLDER);
        realStatement.setNClob(parameterIndex, value);
    }

    @Override
    public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, SQL_PARAMETER_PLACEHOLDER);
        realStatement.setClob(parameterIndex, reader, length);
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, SQL_PARAMETER_PLACEHOLDER);
        realStatement.setBlob(parameterIndex, inputStream, length);
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, SQL_PARAMETER_PLACEHOLDER);
        realStatement.setNClob(parameterIndex, reader, length);
    }

    @Override
    public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, SQL_PARAMETER_PLACEHOLDER);
        realStatement.setSQLXML(parameterIndex, xmlObject);
    }

    @Override
    public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, x);
        realStatement.setObject(parameterIndex, x, targetSqlType, scaleOrLength);
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, SQL_PARAMETER_PLACEHOLDER);
        realStatement.setAsciiStream(parameterIndex, x, length);
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, SQL_PARAMETER_PLACEHOLDER);
        realStatement.setBinaryStream(parameterIndex, x, length);
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, SQL_PARAMETER_PLACEHOLDER);
        realStatement.setCharacterStream(parameterIndex, reader, length);
    }

    @Override
    public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, SQL_PARAMETER_PLACEHOLDER);
        realStatement.setAsciiStream(parameterIndex, x);
    }

    @Override
    public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, SQL_PARAMETER_PLACEHOLDER);
        realStatement.setBinaryStream(parameterIndex, x);
    }

    @Override
    public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, SQL_PARAMETER_PLACEHOLDER);
        realStatement.setCharacterStream(parameterIndex, reader);
    }

    @Override
    public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, SQL_PARAMETER_PLACEHOLDER);
        realStatement.setNCharacterStream(parameterIndex, value);
    }

    @Override
    public void setClob(int parameterIndex, Reader reader) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, SQL_PARAMETER_PLACEHOLDER);
        realStatement.setClob(parameterIndex, reader);
    }

    @Override
    public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, SQL_PARAMETER_PLACEHOLDER);
        realStatement.setBlob(parameterIndex, inputStream);
    }

    @Override
    public void setNClob(int parameterIndex, Reader reader) throws SQLException {
        statementEnhanceInfos.setParameter(parameterIndex, SQL_PARAMETER_PLACEHOLDER);
        realStatement.setNClob(parameterIndex, reader);
    }

}

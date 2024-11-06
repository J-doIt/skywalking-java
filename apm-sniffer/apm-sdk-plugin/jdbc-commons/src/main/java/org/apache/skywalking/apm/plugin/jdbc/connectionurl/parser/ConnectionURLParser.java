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

package org.apache.skywalking.apm.plugin.jdbc.connectionurl.parser;

import org.apache.skywalking.apm.plugin.jdbc.trace.ConnectionInfo;

/** 数据库连接的URL解析器 */
public interface ConnectionURLParser {
    /**
     * {@link ConnectionURLParser} parses database name and the database host(s) from connection url.
     * （ConnectionURLParser 从 连接url 解析 数据库名称 和 数据库主机。）
     *
     * @return jdbc连接信息
     */
    ConnectionInfo parse();
}

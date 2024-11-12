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

package org.apache.skywalking.apm.plugin.trace.ignore.conf;

public class IgnoreConfig {

    public static class Trace {
        /**
         * If the operation name of the first span is matching, this segment should be ignored /path/?   Match any
         * single character /path/*   Match any number of characters /path/**  Match any number of characters and
         * support multilevel directories
         * <pre>
         * (如果第一个 span 的操作名是匹配的，这个 segment 应该被忽略。
         *      /path/？：匹配任意单个字符
         *      /path/* ：匹配任意数量的字符
         *      /path/** ：匹配任意数量的字符并支持多级目录。)
         * </pre>
         */
        public static String IGNORE_PATH = "";
    }
}

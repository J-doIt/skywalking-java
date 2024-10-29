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

package org.apache.skywalking.apm.toolkit.logging.common.log;

import org.apache.skywalking.apm.agent.core.boot.PluginConfig;

public class ToolkitConfig {

    public static class Plugin {
        public static class Toolkit {
            @PluginConfig(root = ToolkitConfig.class)
            public static class Log {
                /**
                 * Whether or not to transmit logged data as formatted or un-formatted.
                 * （是否以 格式化或未格式化 的方式 传输记录的数据。）
                 */
                public static boolean TRANSMIT_FORMATTED = true;
            }
        }
    }
}
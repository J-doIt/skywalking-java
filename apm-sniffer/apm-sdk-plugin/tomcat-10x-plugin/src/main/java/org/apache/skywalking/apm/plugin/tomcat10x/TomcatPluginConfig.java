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

package org.apache.skywalking.apm.plugin.tomcat10x;

import org.apache.skywalking.apm.agent.core.boot.PluginConfig;

public class TomcatPluginConfig {
    public static class Plugin {
        @PluginConfig(root = TomcatPluginConfig.class)
        public static class Tomcat {
            /**
             * This config item controls that whether the Tomcat plugin should collect the parameters of the request.
             * （此配置项控制 Tomcat 插件是否应收集请求的参数。）
             */
            public static boolean COLLECT_HTTP_PARAMS = false;
        }

        @PluginConfig(root = TomcatPluginConfig.class)
        public static class Http {
            /**
             * When either {@link Tomcat#COLLECT_HTTP_PARAMS} is enabled, how many characters to keep and send to the
             * OAP backend, use negative values to keep and send the complete parameters, NB. this config item is added
             * for the sake of performance
             * （启用任一 TomcatPluginConfig.Plugin.Tomcat.COLLECT_HTTP_PARAMS 配置项后，保留多少个字符并发送到 OAP 后端，使用负值保留并发送完整参数，注意。添加此配置项是为了性能）
             */
            public static int HTTP_PARAMS_LENGTH_THRESHOLD = 1024;
        }
    }
}

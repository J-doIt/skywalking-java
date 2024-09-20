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

package org.apache.skywalking.apm.agent.core.plugin;

import org.apache.skywalking.apm.agent.core.plugin.exception.IllegalPluginDefineException;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 插件定义配置，
 * 读取 skywalking-plugin.def 文件，生成插件定义( org.skywalking.apm.agent.core.plugin.PluginDefinie )数组。
 */
public enum PluginCfg {
    INSTANCE;

    private static final ILog LOGGER = LogManager.getLogger(PluginCfg.class);

    private List<PluginDefine> pluginClassList = new ArrayList<PluginDefine>();
    private PluginSelector pluginSelector = new PluginSelector();

    /**
     * 读取 skywalking-plugin.def 文件，添加到 pluginClassList。
     */
    void load(InputStream input) throws IOException {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            String pluginDefine;
            while ((pluginDefine = reader.readLine()) != null) { // 换行
                try {
                    if (pluginDefine.trim().length() == 0 || pluginDefine.startsWith("#")) {
                        continue;
                    }
                    // 解析字符串，创建插件定义
                    PluginDefine plugin = PluginDefine.build(pluginDefine);
                    pluginClassList.add(plugin);
                } catch (IllegalPluginDefineException e) {
                    LOGGER.error(e, "Failed to format plugin({}) define.", pluginDefine);
                }
            }
        } finally {
            input.close();
        }
    }

    public List<PluginDefine> getPluginClassList() {
        pluginClassList = pluginSelector.select(pluginClassList);
        return pluginClassList;
    }

}

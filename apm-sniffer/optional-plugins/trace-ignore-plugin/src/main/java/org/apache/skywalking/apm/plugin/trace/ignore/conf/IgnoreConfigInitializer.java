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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import org.apache.skywalking.apm.agent.core.boot.AgentPackageNotFoundException;
import org.apache.skywalking.apm.agent.core.boot.AgentPackagePath;
import org.apache.skywalking.apm.agent.core.conf.ConfigNotFoundException;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.util.ConfigInitializer;
import org.apache.skywalking.apm.util.PropertyPlaceholderHelper;

public class IgnoreConfigInitializer {
    private static final ILog LOGGER = LogManager.getLogger(IgnoreConfigInitializer.class);
    private static final String CONFIG_FILE_NAME = "/config/apm-trace-ignore-plugin.config";
    private static final String ENV_KEY_PREFIX = "skywalking.";

    /**
     * Try to locate `apm-trace-ignore-plugin.config`, which should be in the /optional-plugins/apm-trace-ignore-plugin/
     * dictionary of agent package.
     * <p>
     * Also try to override the config by system.env and system.properties. All the keys in these two places should
     * start with {@link #ENV_KEY_PREFIX}. e.g. in env `skywalking.trace.ignore_path=your_path` to override
     * `trace.ignore_path` in apm-trace-ignore-plugin.config file.
     * <p>
     *
     * <pre>
     * (
     * 尝试找到 “apm-trace-ignore-plugin.config”，
     *      它应该位于代理软件包的 /optional-plugins/apm-trace-ignore-plugin/ 字典中。
     * 此外，尝试通过 system.env 和 system.properties 覆盖配置。
     *      这两个位置的所有键都应以 {@link #ENV_KEY_PREFIX} 开头。
     *      例如，在 env 'skywalking.trace.ignore_path=your_path' 中覆盖 apm-trace-ignore-plugin.config 文件中的 'trace.ignore_path'。)
     * </pre>
     */
    public static void initialize() {
        // 得到 apm-trace-ignore-plugin.config
        try (final InputStream configFileStream = loadConfigFromAgentFolder()) {
            Properties properties = new Properties();
            // 从输入流中加载配置文件
            properties.load(configFileStream);
            // 遍历所有配置项，替换其中的占位符
            for (String key : properties.stringPropertyNames()) {
                String value = (String) properties.get(key);
                // 替换配置项中的占位符
                properties.put(key, PropertyPlaceholderHelper.INSTANCE.replacePlaceholders(value, properties));
            }
            // 根据 properties 初始化 IgnoreConfig 类
            ConfigInitializer.initialize(properties, IgnoreConfig.class);
        } catch (Exception e) {
            LOGGER.error(e, "Failed to read the config file, skywalking is going to run in default config.");
        }

        try {
            // 通过系统属性覆盖配置
            overrideConfigBySystemProp();
        } catch (Exception e) {
            LOGGER.error(e, "Failed to read the system env.");
        }
    }

    private static void overrideConfigBySystemProp() throws IllegalAccessException {
        Properties properties = new Properties();
        Properties systemProperties = System.getProperties();
        for (final Map.Entry<Object, Object> prop : systemProperties.entrySet()) {
            if (prop.getKey().toString().startsWith(ENV_KEY_PREFIX)) {
                String realKey = prop.getKey().toString().substring(ENV_KEY_PREFIX.length());
                properties.put(realKey, prop.getValue());
            }
        }

        if (!properties.isEmpty()) {
            ConfigInitializer.initialize(properties, IgnoreConfig.class);
        }
    }

    /**
     * Load the config file, where the agent jar is.
     * <pre>
     * (加载 skywalking-agent.jar 所在的配置文件。)
     * </pre>
     *
     * @return the config file {@link InputStream}, or null if not needEnhance.
     */
    private static InputStream loadConfigFromAgentFolder() throws AgentPackageNotFoundException, ConfigNotFoundException {
        // '/skywalking-agent.jar 的父路径/config/apm-trace-ignore-plugin.config'
        File configFile = new File(AgentPackagePath.getPath(), CONFIG_FILE_NAME);
        if (configFile.exists() && configFile.isFile()) {
            try {
                LOGGER.info("Ignore config file found in {}.", configFile);
                return new FileInputStream(configFile);
            } catch (FileNotFoundException e) {
                throw new ConfigNotFoundException("Fail to load apm-trace-ignore-plugin.config", e);
            }
        }
        throw new ConfigNotFoundException("Fail to load ignore config file.");
    }
}
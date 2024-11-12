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

package org.apache.skywalking.apm.agent.core.boot;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;

/**
 * AgentPackagePath is a flag and finder to locate the SkyWalking agent.jar. It gets the absolute path of the agent jar.
 * The path is the required metadata for agent core looking up the plugins and toolkit activations. If the lookup
 * mechanism fails, the agent will exit directly.
 * <pre>
 * (AgentPackagePath 是用于查找 SkyWalking agent jar 的标志和查找器。
 * 它获取 agent jar 的 绝对路径。该路径是 agent-core 查找插件 和 工具包激活 所需的元数据。
 * 如果查找机制失败，代理将直接退出。)
 * </pre>
 */
public class AgentPackagePath {
    private static final ILog LOGGER = LogManager.getLogger(AgentPackagePath.class);

    /** skywalking-agent.jar 的父路径 */
    private static File AGENT_PACKAGE_PATH;

    /**
     * @return skywalking-agent.jar 的父路径
     * @throws AgentPackageNotFoundException
     */
    public static File getPath() throws AgentPackageNotFoundException {
        if (AGENT_PACKAGE_PATH == null) {
            AGENT_PACKAGE_PATH = findPath();
        }
        return AGENT_PACKAGE_PATH;
    }

    public static boolean isPathFound() {
        return AGENT_PACKAGE_PATH != null;
    }

    /**
     * @return skywalking-agent.jar 的父路径
     * @throws AgentPackageNotFoundException
     */
    private static File findPath() throws AgentPackageNotFoundException {
        // 将 AgentPackagePath类名 转换为 资源（文件）路径
        String classResourcePath = AgentPackagePath.class.getName().replaceAll("\\.", "/") + ".class";

        // 获取 AgentPackagePath 类 的 资源（文件） URL
        URL resource = AgentPackagePath.class.getClassLoader().getResource(classResourcePath);
        if (resource != null) {
            String urlString = resource.toString();

            LOGGER.debug("The beacon class location is {}.", urlString);

            // 检查 URL 是否指向一个 JAR 文件
            //      当类文件位于 JAR 文件中时，其 URL 字符串表示会包含一个 ! 字符。
            //      eg：jar:file:/path/to/your.jar!/com/example/YourClass.class
            //          jar:                            表示这是一个 JAR 文件。
            //          file:/path/to/xxx.jar：          这是 JAR 文件的路径。
            //          !：                              分隔 JAR 文件路径 和 JAR 文件内部的资源路径。
            //          /com/example/YourClass.class：   是 JAR 文件内部的资源路径。
            int insidePathIndex = urlString.indexOf('!');
            boolean isInJar = insidePathIndex > -1;

            if (isInJar) {
                // 如果在 JAR 文件中，提取 JAR 文件的路径
                urlString = urlString.substring(urlString.indexOf("file:"), insidePathIndex);
                File agentJarFile = null;
                try {
                    // 将 URL 转换为文件
                    agentJarFile = new File(new URL(urlString).toURI());
                } catch (MalformedURLException | URISyntaxException e) {
                    LOGGER.error(e, "Can not locate agent jar file by url:" + urlString);
                }
                // 检查 JAR 文件是否存在
                if (agentJarFile.exists()) {
                    // 返回 JAR 文件的父目录
                    return agentJarFile.getParentFile();
                }
            } else {
                // 如果不在 JAR 文件中，提取 AgentPackagePath类文件 的路径
                int prefixLength = "file:".length();
                String classLocation = urlString.substring(
                    prefixLength, urlString.length() - classResourcePath.length());
                // 返回类文件所在的目录
                return new File(classLocation);
            }
        }

        LOGGER.error("Can not locate agent jar file.");
        throw new AgentPackageNotFoundException("Can not locate agent jar file.");
    }

}

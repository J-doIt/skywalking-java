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

package org.apache.skywalking.apm.plugin.trace.ignore;

import org.apache.skywalking.apm.agent.core.boot.OverrideImplementor;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.conf.dynamic.ConfigurationDiscoveryService;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.sampling.SamplingService;
import org.apache.skywalking.apm.plugin.trace.ignore.conf.IgnoreConfig;
import org.apache.skywalking.apm.plugin.trace.ignore.conf.IgnoreConfigInitializer;
import org.apache.skywalking.apm.plugin.trace.ignore.matcher.FastPathMatcher;
import org.apache.skywalking.apm.plugin.trace.ignore.matcher.TracePathMatcher;
import org.apache.skywalking.apm.util.StringUtil;

/**
 * 重写 SamplingService
 */
@OverrideImplementor(SamplingService.class)
public class TraceIgnoreExtendService extends SamplingService {
    private static final ILog LOGGER = LogManager.getLogger(TraceIgnoreExtendService.class);
    private static final String PATTERN_SEPARATOR = ",";
    private TracePathMatcher pathMatcher = new FastPathMatcher();
    /** 'trace.ignore_path' 中的配置（分隔后） */
    private volatile String[] patterns = new String[] {};
    /** 动态配置观察器（观察 agent.trace.ignore_path 配置） */
    private TraceIgnorePatternWatcher traceIgnorePatternWatcher;

    @Override
    public void prepare() {
        super.prepare();
    }

    @Override
    public void boot() {
        super.boot();

        // 根据 apm-trace-ignore-plugin.config 初始化 IgnoreConfig 类。（system.env 和 system.properties 优先级高）
        IgnoreConfigInitializer.initialize();
        // 如果 'trace.ignore_path' 配置不为空
        if (StringUtil.isNotEmpty(IgnoreConfig.Trace.IGNORE_PATH)) {
            patterns = IgnoreConfig.Trace.IGNORE_PATH.split(PATTERN_SEPARATOR);
        }

        // 动态配置观察器：关注 ‘agent.trace.ignore_path’
        traceIgnorePatternWatcher = new TraceIgnorePatternWatcher("agent.trace.ignore_path", this);
        // 注册动态配置观察器
        ServiceManager.INSTANCE.findService(ConfigurationDiscoveryService.class)
                               .registerAgentConfigChangeWatcher(traceIgnorePatternWatcher);

        // this.patterns 变化后的处理
        handleTraceIgnorePatternsChanged();
    }

    @Override
    public void onComplete() {
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    @Override
    public boolean trySampling(final String operationName) {
        // 如果 trace.ignore_path 不为空
        if (patterns.length > 0) {
            // span 的 operationName 与之 任一 匹配，则忽略采样。
            for (String pattern : patterns) {
                if (pathMatcher.match(pattern, operationName)) {
                    LOGGER.debug("operationName : " + operationName + " Ignore tracking");
                    return false;
                }
            }
        }
        // super trySampling
        return super.trySampling(operationName);
    }

    @Override
    public void forceSampled() {
        super.forceSampled();
    }

    void handleTraceIgnorePatternsChanged() {
        if (StringUtil.isNotBlank(traceIgnorePatternWatcher.getTraceIgnorePathPatterns())) {
            patterns = traceIgnorePatternWatcher.getTraceIgnorePathPatterns().split(PATTERN_SEPARATOR);
        } else {
            patterns = new String[] {};
        }
    }
}

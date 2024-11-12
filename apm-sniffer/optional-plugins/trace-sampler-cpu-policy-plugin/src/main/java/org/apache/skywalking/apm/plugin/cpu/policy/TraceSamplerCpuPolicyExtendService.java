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

package org.apache.skywalking.apm.plugin.cpu.policy;

import org.apache.skywalking.apm.agent.core.boot.OverrideImplementor;
import org.apache.skywalking.apm.agent.core.boot.ServiceManager;
import org.apache.skywalking.apm.agent.core.jvm.JVMService;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.sampling.SamplingService;
import org.apache.skywalking.apm.plugin.cpu.policy.conf.TraceSamplerCpuPolicyPluginConfig;

/**
 * 重写 SamplingService
 */
@OverrideImplementor(SamplingService.class)
public class TraceSamplerCpuPolicyExtendService extends SamplingService {
    private static final ILog LOGGER = LogManager.getLogger(TraceSamplerCpuPolicyExtendService.class);

    /** CPU 使用率百分比限制开关，默认关闭 */
    private volatile boolean cpuUsagePercentLimitOn = false;
    private volatile JVMService jvmService;

    @Override
    public void prepare() {
        super.prepare();
    }

    @Override
    public void boot() {
        super.boot();
        // 如果 'plugin.cpupolicy.sample_cpu_usage_percent_limit' 大于0
        if (TraceSamplerCpuPolicyPluginConfig.Plugin.CpuPolicy.SAMPLE_CPU_USAGE_PERCENT_LIMIT > 0) {
            LOGGER.info("TraceSamplerCpuPolicyExtendService cpu usage percent limit open");
            // 从 ServiceManager 获取 JVMService 实例
            jvmService = ServiceManager.INSTANCE.findService(JVMService.class);
            // 开关 on
            cpuUsagePercentLimitOn = true;
        }
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
        // 如果开关on
        if (cpuUsagePercentLimitOn) {
            // 获取 CPU使用率百分比
            double cpuUsagePercent = jvmService.getCpuUsagePercent();
            // 如果 当前CPU使用率百分比 > 此限制，该 span 不采样。
            if (cpuUsagePercent > TraceSamplerCpuPolicyPluginConfig.Plugin.CpuPolicy.SAMPLE_CPU_USAGE_PERCENT_LIMIT) {
                return false;
            }
        }
        // super trySampling
        return super.trySampling(operationName);
    }

    @Override
    public void forceSampled() {
        super.forceSampled();
    }

}
